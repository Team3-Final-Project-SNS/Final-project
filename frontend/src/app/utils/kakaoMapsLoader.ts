const KAKAO_MAP_SCRIPT_ID = 'kakao-map-sdk';
const KAKAO_MAP_SCRIPT_URL = 'https://dapi.kakao.com/v2/maps/sdk.js';
const KAKAO_MAP_SCRIPT_SELECTOR = 'script[src*="dapi.kakao.com/v2/maps/sdk.js"]';
const DEFAULT_TIMEOUT_MS = 30000;
const KAKAO_LOAD_CALLBACK_TIMEOUT_MS = 4000;
const KAKAO_READY_POLL_INTERVAL_MS = 50;

let kakaoMapsPromise: Promise<typeof kakao.maps> | null = null;

function isKakaoMapsReady() {
  return Boolean(
    window.kakao?.maps?.LatLng
    && window.kakao?.maps?.Map
    && window.kakao?.maps?.Circle
    && window.kakao?.maps?.CustomOverlay,
  );
}

function isKakaoPlacesReady() {
  return Boolean(
    isKakaoMapsReady()
    && window.kakao?.maps?.services?.Places
    && window.kakao?.maps?.services?.Status,
  );
}

function getKakaoMapAppKey() {
  return import.meta.env.VITE_KAKAO_MAP_APP_KEY || '1e79f120fd9151db28c4df86b37cab4f';
}

function waitForKakaoMapsReady(timeoutMs: number, isReady = isKakaoMapsReady) {
  if (isReady()) {
    return Promise.resolve(window.kakao!.maps);
  }

  return new Promise<typeof kakao.maps>((resolve, reject) => {
    const timeoutId = window.setTimeout(() => {
      window.clearInterval(pollTimerId);
      reject(new Error('Kakao Maps SDK initialization timed out.'));
    }, timeoutMs);

    const pollTimerId = window.setInterval(() => {
      if (!isReady()) {
        return;
      }

      window.clearTimeout(timeoutId);
      window.clearInterval(pollTimerId);
      resolve(window.kakao!.maps);
    }, KAKAO_READY_POLL_INTERVAL_MS);
  });
}

function ensureKakaoScript() {
  if (window.kakao?.maps?.load) {
    return Promise.resolve();
  }

  const existingScript = document.querySelector<HTMLScriptElement>(KAKAO_MAP_SCRIPT_SELECTOR);

  if (existingScript) {
    return new Promise<void>((resolve, reject) => {
      if (window.kakao?.maps?.load) {
        resolve();
        return;
      }

      let pollCount = 0;
      const pollTimerId = window.setInterval(() => {
        pollCount += 1;
        if (window.kakao?.maps?.load) {
          window.clearInterval(pollTimerId);
          resolve();
          return;
        }

        if (pollCount >= 80) {
          window.clearInterval(pollTimerId);
          reject(new Error('Kakao Maps SDK script loaded without loader.'));
        }
      }, 100);

      existingScript.addEventListener(
        'load',
        () => {
          if (window.kakao?.maps?.load) {
            window.clearInterval(pollTimerId);
            resolve();
          }
        },
        { once: true },
      );
      existingScript.addEventListener(
        'error',
        () => {
          window.clearInterval(pollTimerId);
          reject(new Error('Kakao Maps SDK script failed to load.'));
        },
        { once: true },
      );
    });
  }

  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.id = KAKAO_MAP_SCRIPT_ID;
    script.type = 'text/javascript';
    script.src = `${KAKAO_MAP_SCRIPT_URL}?appkey=${getKakaoMapAppKey()}&autoload=false&libraries=services,geometry`;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Kakao Maps SDK script failed to load.'));
    document.head.appendChild(script);
  });
}

function resetStaleKakaoScript() {
  if (isKakaoMapsReady()) return;

  document
    .querySelectorAll<HTMLScriptElement>(KAKAO_MAP_SCRIPT_SELECTOR)
    .forEach((script) => script.remove());

  try {
    delete window.kakao;
  } catch {
    window.kakao = undefined;
  }
}

function loadKakaoMapsWithTimeout(timeoutMs: number) {
  return new Promise<typeof kakao.maps>((resolve, reject) => {
    if (isKakaoMapsReady()) {
      resolve(window.kakao!.maps);
      return;
    }

    if (typeof window.kakao?.maps?.load !== 'function') {
      reject(new Error('Kakao Maps SDK loader is unavailable.'));
      return;
    }

    let isSettled = false;
    const timeoutId = window.setTimeout(() => {
      if (isSettled) return;
      isSettled = true;
      reject(new Error('Kakao Maps SDK load callback timed out.'));
    }, KAKAO_LOAD_CALLBACK_TIMEOUT_MS);

    window.kakao.maps.load(() => {
      if (isSettled) return;

      waitForKakaoMapsReady(timeoutMs)
        .then((kakaoMaps) => {
          if (isSettled) return;
          isSettled = true;
          window.clearTimeout(timeoutId);
          resolve(kakaoMaps);
        })
        .catch((error) => {
          if (isSettled) return;
          isSettled = true;
          window.clearTimeout(timeoutId);
          reject(error);
        });
    });
  });
}

async function loadKakaoMapsOnce(timeoutMs: number) {
  await ensureKakaoScript();

  if (isKakaoMapsReady()) {
    return window.kakao!.maps;
  }

  return loadKakaoMapsWithTimeout(timeoutMs);
}

export function loadKakaoMaps(timeoutMs = DEFAULT_TIMEOUT_MS) {
  if (isKakaoMapsReady()) {
    return Promise.resolve(window.kakao!.maps);
  }

  if (!kakaoMapsPromise) {
    kakaoMapsPromise = loadKakaoMapsOnce(timeoutMs)
      .catch((firstError) => {
        if (isKakaoMapsReady()) {
          return window.kakao!.maps;
        }

        console.warn('Retrying Kakao Maps SDK after stale load state.', firstError);
        resetStaleKakaoScript();
        return loadKakaoMapsOnce(timeoutMs);
      })
      .catch((error) => {
        kakaoMapsPromise = null;
        throw error;
      });
  }

  return kakaoMapsPromise;
}

export async function loadKakaoPlaceServices(timeoutMs = DEFAULT_TIMEOUT_MS) {
  await loadKakaoMaps(timeoutMs);
  return waitForKakaoMapsReady(timeoutMs, isKakaoPlacesReady);
}

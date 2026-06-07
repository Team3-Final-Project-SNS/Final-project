type StreamPostOptions<TBody> = {
  path: string;
  body: TBody;
  admin?: boolean;
  onChunk: (chunk: string) => void;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export async function streamPost<TBody>({ path, body, admin = false, onChunk }: StreamPostOptions<TBody>) {
  const tokenKey = admin ? 'adminAccessToken' : 'accessToken';
  const token = sessionStorage.getItem(tokenKey);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'ngrok-skip-browser-warning': 'true',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(await resolveErrorMessage(response));
  }

  if (!response.body) {
    return '';
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let answer = '';

  while (true) {
    const { value, done } = await reader.read();

    if (done) {
      break;
    }

    const chunk = parseSseChunk(decoder.decode(value, { stream: true }));

    if (chunk) {
      answer += chunk;
      onChunk(chunk);
    }
  }

  const tail = parseSseChunk(decoder.decode());

  if (tail) {
    answer += tail;
    onChunk(tail);
  }

  return answer;
}

function parseSseChunk(chunk: string) {
  const lines = chunk.split(/\r?\n/);
  const parsed = lines
    .map((line) => {
      if (line.startsWith('data:')) {
        return line.slice(5).replace(/^ /, '');
      }

      if (
        line.length === 0 ||
        line.startsWith('event:') ||
        line.startsWith('id:') ||
        line.startsWith('retry:')
      ) {
        return '';
      }

      return line;
    })
    .join('');

  return parsed;
}

async function resolveErrorMessage(response: Response) {
  try {
    const contentType = response.headers.get('content-type') || '';

    if (contentType.includes('application/json')) {
      const json = await response.json();
      return json.message || json.error || `요청에 실패했습니다. (${response.status})`;
    }

    const text = await response.text();
    return text || `요청에 실패했습니다. (${response.status})`;
  } catch {
    return `요청에 실패했습니다. (${response.status})`;
  }
}

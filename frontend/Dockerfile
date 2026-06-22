# Node.js 20 이미지를 베이스로 사용
FROM node:20-alpine

# 컨테이너 안의 작업 디렉토리 설정
WORKDIR /app

# pnpm 설치 (프로젝트가 pnpm 사용하므로)
RUN npm install -g pnpm

# package.json, pnpm-lock.yaml 먼저 복사 (의존성 캐시 활용)
COPY package.json pnpm-lock.yaml ./

# 의존성 설치
RUN pnpm install

# 나머지 소스코드 전체 복사
COPY . .

# 5173 포트 외부에 노출
EXPOSE 5173

# 컨테이너 시작 시 실행할 명령어 (--host = 외부 접근 허용)
CMD ["pnpm", "dev", "--host"]
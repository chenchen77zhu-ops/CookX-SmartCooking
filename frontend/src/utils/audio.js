import { resolveBackendUrl } from '@/config/backend'

// 封装一个简单的播放器工具
export const playVoice = (url) => {
  const audio = new Audio(resolveBackendUrl(url));
  audio.play();
};

// 封装一个简单的播放器工具
export const playVoice = (url) => {
  const fullUrl = `http://localhost:8000${url}`;
  const audio = new Audio(fullUrl);
  audio.play();
};
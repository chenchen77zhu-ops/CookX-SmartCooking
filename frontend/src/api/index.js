import axios from 'axios'
const request = axios.create({ baseURL: 'http://localhost:8000/api' })

export const uploadImg = (file) => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/upload', fd);
}
export const getRecipe = () => request.get('/recommend');
export const getTTS = (text) => request.get('/tts', { params: { text } });
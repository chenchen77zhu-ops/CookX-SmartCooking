import axios from 'axios'
const request = axios.create({ baseURL: '/api' })

export const uploadImg = (file) => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/analyze-fridge', fd);
}
export const getRecipe = (userPrompt, userId, saveHistory = true) => request.get('/recommend-recipe', {
    params: { user_prompt: userPrompt, user_id: userId, save_history: saveHistory }
});
export const getTTS = (text) => request.get('/tts', { params: { text } });

import axios from 'axios'
import { API_BASE_URL } from '@/config/backend'

const request = axios.create({ baseURL: API_BASE_URL })

export const uploadImg = (file) => {
    const fd = new FormData(); fd.append('file', file);
    return request.post('/analyze-fridge', fd);
}
export const getRecipe = (userPrompt, userId, saveHistory = true) => request.get('/recommend-recipe', {
    params: { user_prompt: userPrompt, user_id: userId, save_history: saveHistory }
});
export const getMultiObjectiveRecommendations = (payload, signal) => request.post('/recommendations', payload, { signal });
export const getTTS = (text) => request.get('/tts', { params: { text } });

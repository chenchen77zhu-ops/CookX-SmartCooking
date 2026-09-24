import {normalizeRecipe} from './recipeAdapter'
const key=user=>`cookx:recipe-draft:v1:${user}`
export function queueRecipeDraft(user,recipe){if(!user)throw new Error('请先登录');sessionStorage.setItem(key(user),JSON.stringify(normalizeRecipe(recipe)))}
export function takeRecipeDraft(user){const raw=sessionStorage.getItem(key(user));if(!raw)return null;const recipe=normalizeRecipe(JSON.parse(raw));sessionStorage.removeItem(key(user));return recipe}

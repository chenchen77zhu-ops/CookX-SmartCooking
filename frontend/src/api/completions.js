import {createCompletionSync} from '../services/completionSync'
import {businessApi} from './business'
import {readUserId} from '../services/recognitionDraft'
export const completionSync=createCompletionSync({storage:localStorage,api:businessApi,currentUser:readUserId})

package repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import network.ApiService
import network.DocumentApiState
import network.request.APIRequest
import network.response.APIResponse

class DocumentsRepository(private val apiService: ApiService) {
    suspend fun getDocuments(request: APIRequest): Flow<DocumentApiState<APIResponse>> {
        return flow {

            val document = apiService.getDocuments(request);

            // Emit this data wrapped in
            // the helper class [CommentApiState]
            emit(DocumentApiState.success(document))
        }.flowOn(Dispatchers.IO)
    }
}
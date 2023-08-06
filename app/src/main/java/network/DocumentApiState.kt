package network

data class DocumentApiState<out T>(val status: Status, val data: T?, val message: String?) {

    companion object {

        // In case of Success,set status as
        // Success and data as the response
        fun <T> success(data: T?): DocumentApiState<T> {
            return DocumentApiState(Status.SUCCESS, data, null)
        }

        // In case of failure ,set state to Error ,
        // add the error message,set data to null
        fun <T> error(msg: String): DocumentApiState<T> {
            return DocumentApiState(Status.ERROR, null, msg)
        }

        // When the call is loading set the state
        // as Loading and rest as null
        fun <T> loading(): DocumentApiState<T> {
            return DocumentApiState(Status.LOADING, null, null)
        }

        // When the call is disabled set the state
        // as initialized and rest as null
        fun <T> init(): DocumentApiState<T> {
            return DocumentApiState(Status.INIT, null, null)
        }
    }
}

// An enum to store the
// current state of api call
enum class Status {
    INIT,
    SUCCESS,
    ERROR,
    LOADING
}

package network.request

class APIRequest(
    var collection: String,
    var dataSource: String,
    var database: String,
    var limit: Int,
    sortVal: Int,
    filterVal: String) {

    private var sort: Sort? = null

    private var filter: Filter? = null

    init {
        sort = Sort(sortVal)
        filter = Filter(Timestamp(filterVal))
    }
}


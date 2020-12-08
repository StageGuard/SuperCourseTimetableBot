package stageguard.sctimetable.database

class InvalidDatabaseConfigException : Exception {
    constructor(message : String) {
        java.lang.Exception(message)
    }
}

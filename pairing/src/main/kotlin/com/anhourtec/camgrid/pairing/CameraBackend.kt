package com.anhourtec.camgrid.pairing

/** An IP camera as listed on the dashboard. Passwords are never part of it. */
data class CameraEntry(
    val id: String,
    val name: String,
    val address: String,
    val ownLogin: Boolean,
    val user: String,
    val hasOwnPassword: Boolean,
)

/** A camera the network scan found; [added] when one with this address is already saved. */
data class FoundCamera(
    val host: String,
    val rtspPort: Int?,
    val onvif: Boolean,
    val added: Boolean,
)

/** A brand preset the user can pick ([id] is posted back). */
data class BrandOption(
    val id: String,
    val label: String,
)

data class CamerasSnapshot(
    val cameras: List<CameraEntry>,
    val sharedUser: String,
    val sharedPasswordSet: Boolean,
    val brands: List<BrandOption>,
)

/** How the camera's stream addresses are found. */
enum class AddMode(
    val id: String,
) {
    ONVIF("onvif"),
    BRAND("brand"),
    URL("url"),
    ;

    companion object {
        fun of(id: String?): AddMode = entries.firstOrNull { it.id == id } ?: ONVIF
    }
}

/**
 * The add/edit form, already validated by [CameraRoutes]. An empty [password] means "keep the saved
 * one" when editing, or "no password" for a new camera with its own login.
 */
data class CameraForm(
    val id: String?,
    val mode: AddMode,
    val name: String,
    val host: String,
    val brand: String?,
    val channel: Int,
    val rtspUrl: String,
    val snapshotUrl: String,
    val ownLogin: Boolean,
    val user: String,
    val password: String,
) {
    /** Never print the password, even by accident. */
    override fun toString() = "CameraForm(id=$id, mode=$mode, name=$name, host=$host)"
}

enum class CameraError {
    WRONG_LOGIN,
    UNREACHABLE,
    NO_VIDEO,
    NO_ONVIF,
    NO_SHARED_PASSWORD,

    /** The address changed: a saved password is never sent to a new address without retyping it. */
    PASSWORD_REQUIRED,

    /** The camera only accepts Basic login: allowed only with a password typed in this form. */
    BASIC_NEEDS_TYPED_PASSWORD,
}

sealed interface SaveOutcome {
    /** Saved; [basicLogin] when the camera only accepts Basic login (password sent readable). */
    data class Saved(
        val basicLogin: Boolean,
    ) : SaveOutcome

    data class Failed(
        val error: CameraError,
    ) : SaveOutcome
}

/** Lets the dashboard read and change the TV's IP cameras. Calls run on server threads and may block. */
interface CameraBackend {
    fun cameras(): CamerasSnapshot

    /** The saved camera as a form to edit (password empty), or null. */
    fun form(id: String): CameraForm?

    /** Looks for cameras on the TV's network (a few seconds). */
    fun scan(): List<FoundCamera>

    /** Tests the camera with its login and saves it only if video can be opened. */
    fun save(form: CameraForm): SaveOutcome

    fun delete(id: String)

    /** Saves the shared login; an empty [password] keeps the saved one. */
    fun setShared(
        user: String,
        password: String,
    )
}

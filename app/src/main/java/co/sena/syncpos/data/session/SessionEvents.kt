package co.sena.syncpos.data.session

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Canal de avisos sobre la sesion. Cuando el servidor responde 401 (token vencido o
// revocado), el repositorio no conoce la navegacion; solo avisa aqui y AppNavigation,
// que si conoce las pantallas, lleva al usuario al Login.
// Un SharedFlow es como un StateFlow pero sin "valor actual": solo emite eventos.
class SessionEvents {

    // extraBufferCapacity = 1 permite emitir sin suspender (tryEmit) aunque nadie escuche aun.
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}

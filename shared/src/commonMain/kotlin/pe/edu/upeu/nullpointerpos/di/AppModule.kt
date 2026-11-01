// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.di
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf
import pe.edu.upeu.nullpointerpos.platform.*
import pe.edu.upeu.nullpointerpos.data.local.LocalStore
import pe.edu.upeu.nullpointerpos.data.remote.*
import pe.edu.upeu.nullpointerpos.data.repository.PosRepositoryRest
import pe.edu.upeu.nullpointerpos.domain.repository.PosRepository
import pe.edu.upeu.nullpointerpos.domain.usecase.*
import pe.edu.upeu.nullpointerpos.presentation.PosViewModel
private var iniciado = false
fun initApp() {
    if (iniciado) return
    startKoin { modules(module {
        single { preferencias() }; single { LocalStore(get()) }; single { SessionContext() }
        single { crearCliente(motorHttp()) }; single { PosApi(get(), get(), get()) }; single { CocinaSocket(get()) }
        single<PosRepository> { PosRepositoryRest(get(), get()) }
        factory { GuardarComanda(get()) }; factory { EnviarComanda(get()) }
        viewModelOf(::PosViewModel)
    }) }
    iniciado = true
}

// Autor: Randy Huaman

package pe.edu.upeu.nullpointerpos.domain.usecase
import pe.edu.upeu.nullpointerpos.domain.model.*
import pe.edu.upeu.nullpointerpos.domain.repository.PosRepository
class GuardarComanda(private val repository: PosRepository) {
    suspend operator fun invoke(b: Borrador): Pedido {
        require(!b.creacionIncierta) { "Primero recupera el pedido por ID para evitar duplicados." }
        require(b.lineas.isNotEmpty()) { "Agrega al menos un plato." }
        require(b.lineas.all { it.cantidad in 1..999 && it.observaciones.length <= 500 }) { "Revisa cantidades y observaciones." }
        require(b.observaciones.length <= 1000) { "La nota general admite hasta 1000 caracteres." }
        return b.idPedido?.let { repository.editar(it, b.lineas) } ?: repository.crear(b)
    }
}
class EnviarComanda(private val repository: PosRepository) {
    suspend operator fun invoke(id: Long): Pedido {
        val actual = repository.pedido(id)
        return if (actual.estado == "BORRADOR") repository.enviar(id) else actual
    }
}

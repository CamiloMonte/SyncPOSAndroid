package co.sena.syncpos.data.remote

import co.sena.syncpos.util.parseMoney
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonPrimitive

// Un "serializer" le ensena a kotlinx.serialization como leer un campo concreto del JSON.
// Este lee dinero que puede llegar como numero (48500) O como texto ("48500.00") y
// siempre lo entrega como Double. Se usa asi en un DTO:
//     @Serializable(with = MoneySerializer::class) val total: Double
object MoneySerializer : KSerializer<Double> {

    override val descriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        // Estamos leyendo JSON, asi que el decoder es un JsonDecoder: podemos pedir
        // el elemento "crudo" y mirar su texto sin importar si venia con comillas o no.
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return parseMoney(element.jsonPrimitive.content)
    }

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

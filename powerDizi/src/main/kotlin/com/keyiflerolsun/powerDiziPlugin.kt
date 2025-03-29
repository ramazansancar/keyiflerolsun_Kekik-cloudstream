import android.content.Context

@CloudstreamPlugin
class powerDiziPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(powerDizi())
    }
}
import com.fasterxml.jackson.annotation.JsonProperty


data class CanliTvResult(
    @JsonProperty("IsSucceeded") val isSucceeded: Boolean?,
    @JsonProperty("Data") val dataResult: DataResult

)

data class DataResult(
    @JsonProperty("AllChannels") val allChannels: List<ChannelResult>?
)

data class ChannelResult(
    @JsonProperty("UId") val uid: String?,
    @JsonProperty("Name") val name: String?,
    @JsonProperty("PrimaryLogoImageUrl") val primaryLogo: String?,
    @JsonProperty("SecondaryLogoImageUrl") val secondaryLogo: String?,
    @JsonProperty("QualityTypeLogoUrl") val qualityLogo: String?,
    @JsonProperty("StreamData") val streamData: StreamData?,
    @JsonProperty("Categories") val categories: List<Category>?,
)

data class StreamData(
    @JsonProperty("HlsStreamUrl") val hlsStreamUrl: String?,
    @JsonProperty("DashStreamUrl") val dashStreamUrl: String?,
)

data class Category(
    @JsonProperty("UId") val uid: String?,
    @JsonProperty("Name") val name: String?,
)
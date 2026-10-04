package com.mistakebook.net.mineru

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Url

/**
 * MinerU 云 API v4：Base `https://mineru.net/api/v4`，认证 `Authorization: Bearer <key>`。
 *
 * 路径与字段名严格按 PRD 4.1（已实测验证），禁止改动。
 */
interface MineruApi {

    /** Step 1 申请上传地址。 */
    @POST("file-urls/batch")
    suspend fun fileUrlsBatch(
        @Header("Authorization") authorization: String,
        @Body request: FileUrlsRequest
    ): Response<MineruEnvelope<FileUrlsData>>

    /** Step 2 PUT 上传文件字节（不带 Authorization 头）。 */
    @PUT
    suspend fun uploadFile(
        @Url uploadUrl: String,
        @Body body: RequestBody
    ): Response<ResponseBody>

    /** Step 3 轮询结果；[batchId] 传 "ping" 可用于测试 Key 是否有效。 */
    @GET("extract-results/batch/{batch_id}")
    suspend fun extractResults(
        @Header("Authorization") authorization: String,
        @Path("batch_id") batchId: String
    ): Response<MineruEnvelope<ExtractResultsData>>

    /** Step 4 下载结果 zip（签名 URL，不带认证头）。 */
    @GET
    suspend fun downloadZip(@Url zipUrl: String): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://mineru.net/api/v4/"
    }
}

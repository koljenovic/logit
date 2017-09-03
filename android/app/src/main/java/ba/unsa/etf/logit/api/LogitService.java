package ba.unsa.etf.logit.api;

import java.util.ArrayList;
import java.util.List;

import ba.unsa.etf.logit.model.Attendance;
import ba.unsa.etf.logit.model.Place;
import ba.unsa.etf.logit.model.Session;
import ba.unsa.etf.logit.model.User;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface LogitService {
    @FormUrlEncoded
    @POST("auth/")
    Call<User> auth(@Field("user") String user, @Field("pass") String pass, @Field("cert") String cert, @Field("uid") String uid);

    @POST("validate/")
    Call<List<Attendance>> validate(@Body List<String> attns);

    @POST("sync/")
    Call<ResponseBody> sync(@Body Session session);

    @Headers({
            "User-Agent: ETF Logit v1.0b /SAPERE AVDE/",
            "Referrer: http://etf.unsa.ba/"
    })
    @GET("reverse")
    Call<Place> getAddress(@Query("email") String email, @Query("format") String format, @Query("lat") double lat, @Query("lon") double lon, @Query("zoom") int zoom, @Query("addressdetails") int addressdetails);
}

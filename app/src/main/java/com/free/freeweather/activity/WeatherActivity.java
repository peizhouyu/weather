package com.free.freeweather.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.bumptech.glide.Glide;
import com.free.freeweather.R;
import com.free.freeweather.db.WeatherCityCode;
import com.free.freeweather.gson.Basic;
import com.free.freeweather.gson.Forecast;
import com.free.freeweather.gson.Weather;
import com.free.freeweather.service.AutoUpdateService;
import com.free.freeweather.service.NotificationService;
import com.free.freeweather.util.HttpUtil;
import com.free.freeweather.util.LocationAssist;
import com.free.freeweather.util.Utility;
import com.free.freeweather.util.gaoDeSet;
import com.free.freeweather.util.queryAPI;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class WeatherActivity extends BasicActivity {

    private ScrollView weatherLayout;

    private TextView titleCity;

    private TextView titleUpdateTime;

    private TextView degreeText;

    private TextView weatherInfoText;

    private LinearLayout forecastLayout;

    private TextView aqiText;

    private TextView pm25Text;

    private TextView comfortText;

    private TextView carWashText;

    private TextView sportText;

    private ImageView bingPicImg;

    public SwipeRefreshLayout swipeRefresh;

    public DrawerLayout drawerLayout;

    private Button navButton;

    private String mWeatherId;
    private ProgressDialog progressDialog;

    //  init SDK
    private AMapLocationClient locationClient = null;
    private AMapLocationClientOption locationOption = new AMapLocationClientOption();

    private List<WeatherCityCode> weatherCityCodeListByDistrict;
    private List<WeatherCityCode> weatherCityCodeListByCity;
    //GPS获得的区名
    public  String district;
    //GPS获得的市名
    public String city;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }
        setContentView(R.layout.activity_weather);
        // 初始化各控件
        bingPicImg = (ImageView) findViewById(R.id.bing_pic_img);
        weatherLayout = (ScrollView) findViewById(R.id.weather_layout);
        titleCity = (TextView) findViewById(R.id.title_city);
        titleUpdateTime = (TextView) findViewById(R.id.title_update_time);
        degreeText = (TextView) findViewById(R.id.degree_text);
        weatherInfoText = (TextView) findViewById(R.id.weather_info_text);
        forecastLayout = (LinearLayout) findViewById(R.id.forecast_layout);
        aqiText = (TextView) findViewById(R.id.aqi_text);
        pm25Text = (TextView) findViewById(R.id.pm25_text);
        comfortText = (TextView) findViewById(R.id.comfort_text);
        carWashText = (TextView) findViewById(R.id.car_wash_text);
        sportText = (TextView) findViewById(R.id.sport_text);
        swipeRefresh = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        navButton = (Button) findViewById(R.id.nav_button);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String weatherString = prefs.getString("weather", null);
        if (weatherString != null) {
            // 有缓存时直接解析天气数据
            Weather weather = Utility.handleWeatherResponse(weatherString);
            mWeatherId = weather.basic.weatherId;
            Log.d("pei1",weather.basic.weatherId);
            showWeatherInfo(weather);
        } else {
            // 无缓存时去服务器查询天气
            mWeatherId = getIntent().getStringExtra("weather_id");
            Log.d("pei2",mWeatherId);
            weatherLayout.setVisibility(View.INVISIBLE);
            requestWeather(mWeatherId);
        }
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
               // requestWeather(mWeatherId);
                Toast.makeText(WeatherActivity.this, "正在自动定位，请稍后...", Toast.LENGTH_LONG).show();
                initLocation();
                startLocation();


                //显示环形进度条
                showProgressDialog();
            }
        });
        navButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
        String bingPic = prefs.getString("bing_pic", null);
        if (bingPic != null) {
            Glide.with(this).load(bingPic).into(bingPicImg);
        } else {
            loadBingPic();
        }
    }

    private void initLocation() {
        //初始化client
        locationClient = new AMapLocationClient(this.getApplicationContext());
        //设置定位参数
        locationClient.setLocationOption(gaoDeSet.getDefaultOption());
        // 设置定位监听
        locationClient.setLocationListener(locationListener);
    }

    /**
     * 定位监听
     */
    AMapLocationListener locationListener = new AMapLocationListener() {
        @Override
        public void onLocationChanged(AMapLocation loc) {
            if (null != loc) {
                //得到结果后即关闭定位刷新
                stopLocation();
                System.out.println("得到位置信息");

                //解析定位结果
                Toast.makeText(WeatherActivity.this,"success",Toast.LENGTH_LONG).show();
                closeProgressDialog();
                //获取SDK返回的区名（eg.  兴县）

                district= loc.getDistrict();
                Log.d("pei","SDK返回的区域信息："+district);
                city = loc.getCity();
                //将返回的太原市截取为太原
                city = city.substring(0,2);
                Log.d("pei","SDK返回的城市信息："+city);
                queryWeatherCodeByDistrict(district, city);
            } else {
                Toast.makeText(WeatherActivity.this,"fail",Toast.LENGTH_LONG).show();
                Log.d("pei","定位失败");
            }
        }
    };

    /**
     * 根据GPS获得的区名去查询相应的天气预报码。
     *
     */
    public void queryWeatherCodeByDistrict(String result, String resultExtra){
        Log.d("pei","根据GPS获得的区名去查询相应的天气预报码");
        Log.d("pei","result的值为："+result);
        Log.d("pei","resultExtra的值为："+resultExtra);
        district = result;
        city = resultExtra;
        // System.out.println(weatherCityCodeList.size());
        weatherCityCodeListByDistrict  = DataSupport.where("cityZh=?",district).find(WeatherCityCode.class);
        weatherCityCodeListByCity = DataSupport.where("leaderZh=?",city).find(WeatherCityCode.class);
        Log.d("pei","根据district查询出来的数据大小"+weatherCityCodeListByDistrict.size());
        Log.d("pei","根据city查询出来的数据大小"+weatherCityCodeListByCity.size());
        System.out.println("sadasdasd");
        if (weatherCityCodeListByDistrict.size() > 0){
            mWeatherId = weatherCityCodeListByDistrict.get(0).getWeatherCityId();
            Log.d("pei","第一个麻麻："+mWeatherId);
            requestWeather(mWeatherId);


        }else if (weatherCityCodeListByCity.size() > 0){
            mWeatherId = weatherCityCodeListByCity.get(0).getWeatherCityId();
            Log.d("pei","第一个麻麻："+mWeatherId);
            requestWeather(mWeatherId);
        }else {
            //数据库无缓存 从服务获取json对应码
            String address = queryAPI.getWeatherCityCodeUrl;
            queryFromServer(address);
        }
    }

    //从服务器查询json对应码
    private void queryFromServer(final String address){
        Log.d("pei","从服务器查询json对应码");
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        Toast.makeText(WeatherActivity.this, "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                boolean resultOk = false;
                String responseText = response.body().string();
                resultOk = Utility.handleWeatherCodeResponse(responseText);
                Log.d("pei","执行到了handleWeatherCodeResponse");
                if (resultOk){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            queryWeatherCodeByDistrict(district, city);

                        }
                    });
                }
            }
        });
    }


    /**
     * 根据天气id请求城市天气信息。
     */
    public void requestWeather(final String weatherId) {
        Log.d("pei","第二个麻麻："+weatherId);
        String weatherUrl = queryAPI.weatherUrl + weatherId + "&key=" + queryAPI.userKey;
        HttpUtil.sendOkHttpRequest(weatherUrl, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseText = response.body().string();
                final Weather weather = Utility.handleWeatherResponse(responseText);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (weather != null && "ok".equals(weather.status)) {
                            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(WeatherActivity.this).edit();
                            editor.putString("weather", responseText);
                            editor.apply();
                            mWeatherId = weather.basic.weatherId;
                            showWeatherInfo(weather);
                            //启动服务
//                            Intent intent = new Intent(WeatherActivity.this, NotificationService.class);
//                            Bundle bundle = new Bundle();
//                            bundle.putSerializable("weather",weather);
//                            intent.putExtras(bundle);
//                            startService(intent);
                        } else {
                            Toast.makeText(WeatherActivity.this, "获取天气信息失败", Toast.LENGTH_SHORT).show();
                        }
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WeatherActivity.this, "获取天气信息失败", Toast.LENGTH_SHORT).show();
                        swipeRefresh.setRefreshing(false);
                    }
                });
            }
        });
        loadBingPic();
    }

    /**
     * 加载必应每日一图
     */
    private void loadBingPic() {
        String requestBingPic = queryAPI.getBingyingImg;
        HttpUtil.sendOkHttpRequest(requestBingPic, new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String bingPic = response.body().string();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(WeatherActivity.this).edit();
                editor.putString("bing_pic", bingPic);
                editor.apply();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Glide.with(WeatherActivity.this).load(bingPic).into(bingPicImg);
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * 处理并展示Weather实体类中的数据。
     */
    private void showWeatherInfo(Weather weather) {
        String cityName = weather.basic.cityName;
        String updateTime = weather.basic.update.updateTime.split(" ")[1];
        String degree = weather.now.temperature + "℃";
        String weatherInfo = weather.now.more.info;
        titleCity.setText(cityName);
        titleUpdateTime.setText(updateTime);
        degreeText.setText(degree);
        weatherInfoText.setText(weatherInfo);
        forecastLayout.removeAllViews();
        for (Forecast forecast : weather.forecasts) {
            View view = LayoutInflater.from(this).inflate(R.layout.forecast_item, forecastLayout, false);
            TextView dateText = (TextView) view.findViewById(R.id.date_text);
            TextView infoText = (TextView) view.findViewById(R.id.info_text);
            TextView maxText = (TextView) view.findViewById(R.id.max_text);
            TextView minText = (TextView) view.findViewById(R.id.min_text);
            dateText.setText(forecast.date);
            infoText.setText(forecast.more.info);
            maxText.setText(forecast.temperature.max);
            minText.setText(forecast.temperature.min);
            Log.d("pei","下拉刷新");
            forecastLayout.addView(view);
        }
        if (weather.aqi != null) {
            aqiText.setText(weather.aqi.city.aqi);
            pm25Text.setText(weather.aqi.city.pm25);
        }
        String comfort = "舒适度：" + weather.suggestion.comfort.info;
        String carWash = "洗车指数：" + weather.suggestion.carWash.info;
        String sport = "运行建议：" + weather.suggestion.sport.info;
        comfortText.setText(comfort);
        carWashText.setText(carWash);
        sportText.setText(sport);
        weatherLayout.setVisibility(View.VISIBLE);
        Intent intent = new Intent(this, AutoUpdateService.class);
        startService(intent);
    }

    //处理由WeatherActivity加载的MenuFragment 启动ChooseWeatherActivity加载的ChooseAreaFragment 运行结束后返回的数据
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //Log.d("123","执行了result方法");
        switch (requestCode ){
            case 1:
                if (resultCode == 200) {
                    swipeRefresh.setRefreshing(true);
                    requestWeather(data.getStringExtra("weather_id"));
                    //Log.d("zhuyu",data.getStringExtra("weather_id"));
                }
                break;
        }
    }



    /**
     * 开始定位
     *
     * @since 2.8.0
     * @author hongming.wang
     *
     */
    private void startLocation(){

        // 设置定位参数
        locationClient.setLocationOption(locationOption);
        // 启动定位
        locationClient.startLocation();
    }

    /**
     * 停止定位
     *
     * @since 2.8.0
     * @author hongming.wang
     *
     */
    private void stopLocation(){
        // 停止定位
        locationClient.stopLocation();
    }

    /**
     * 销毁定位
     *
     * @since 2.8.0
     * @author hongming.wang
     *
     */
    private void destroyLocation(){
        if (null != locationClient) {
            /**
             * 如果AMapLocationClient是在当前Activity实例化的，
             * 在Activity的onDestroy中一定要执行AMapLocationClient的onDestroy
             */
            locationClient.onDestroy();
            locationClient = null;
            locationOption = null;
        }
    }



    /**
     * 显示进度对话框
     */
    private void showProgressDialog() {

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在获取您的位置信息...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }


    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }


}

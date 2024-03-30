package com.atguigu.gulimall.thirdpart;

import com.aliyun.oss.OSSClient;
import com.atguigu.gulimall.thirdpart.component.SmsComponent;
import com.atguigu.gulimall.thirdpart.utils.HttpUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GulimallThirdPartApplicationTests {

    @Autowired
    private OSSClient ossClient;

    @Autowired
    private SmsComponent smsComponent;

    @Test
    public void contextLoads() throws FileNotFoundException {
        InputStream inputStream = new FileInputStream("G:\\MyPicture\\baola.jpg");
        ossClient.putObject("gulimall-thirdpart","baola.jpg", inputStream);
        System.out.println("ok");

    }

//    @Test
//    public void sendCode(){
//        smsComponent.sendCode("13548896936", "bdf2");
//    }

//    @Test
//    public void testSendCoe(){
//        String host = "https://dfsns.market.alicloudapi.com";
//        String path = "/data/send_sms";
//        String method = "POST";
//        String appcode = "241f5aaabe8043a090f79b5a79b7a663";
//        Map<String, String> headers = new HashMap<String, String>();
//        //最后在header中的格式(中间是英文空格)为Authorization:APPCODE 83359fd73fe94948385f570e3c139105
//        headers.put("Authorization", "APPCODE " + appcode);
//        //根据API的要求，定义相对应的Content-Type
//        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
//        Map<String, String> querys = new HashMap<String, String>();
//        Map<String, String> bodys = new HashMap<String, String>();
//        bodys.put("content", "code:1234");
//        bodys.put("template_id", "CST_ptdie100");  //该模板为调试接口专用，短信下发有受限制，调试成功后请联系客服报备专属模板
//        bodys.put("phone_number", "13548896936");
//
//
//        try {
//            HttpResponse response = HttpUtils.doPost(host, path, method, headers, querys, bodys);
////            System.out.println(response.toString());
//            //获取response的body
//            System.out.println(EntityUtils.toString(response.getEntity()));
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

}

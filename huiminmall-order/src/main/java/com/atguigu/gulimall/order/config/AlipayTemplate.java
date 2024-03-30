package com.atguigu.gulimall.order.config;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.atguigu.gulimall.order.vo.PayVo;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "alipay")
@Component
@Data
public class AlipayTemplate {

    //在支付宝创建的应用的id
    private   String app_id = "9021000122686576";

    // 商户私钥，您的PKCS8格式RSA2私钥
    private  String merchant_private_key = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCWxH6kul5XQ7k4nf0dL/TWIt2wkedAwl0i0gDBa9A9uiVSEhl7Jimt3MtgPAQiYWFwD1vlTI6Uo/yKeW4K0Btq5lHQAJBSEO/9cEhri3XKgNNZc3bKJWNVvLmsy1eV1o2EpIl6Hv3KF91yd2VUmSIQOgh0slt/bTdZktBZKp2HAi6Iy6/0P2F5TRPXFau35utLwB1nPwBUz3BMty/NwVwqZI6B0UTIimU6diYWlpG8MS5wuovkATqAGFeMuoBywApZRLZW+9bAdTsfPd/ABwLaxAGLBSNZIw9oDMwTWnS4zEyaNZmAY4zMdo4kgtENXEqXVPB5I0z6W7M1AwKaWsMPAgMBAAECggEAHF4OYho64/p3IEMBu/so+x2GTtG1DEdF1OZkhAAwJ10d5opHRxeQIgl4Lrw8HufpuHb7WHKGJUuRmdBtNxWfAwcg/50LipVrQmldBvcvi5GfsqX6BODyBtDCstNzwFqlN/7paxPntp206fOzGi7R7PGuY56nfptjHbR1BUHyb9NcVPWEFb1ZGBwmbhxDRxSBIeeJgDa3+K6wvHVDQ5NyBz7+SHJay7TGGRGa0aE63/62Cy7+b2HFVuO6U66yXkUKT5jBpjSQ0FDzYSX0aZWPtE6RqvZscD7qNxy8IY/CHIr4dq1rmyj1EivtM3BqUmJM5SWyiSyy5zvFNXV+TUcr2QKBgQDGfIHQDrBFq5wqVicjDswVOB19nMLlIvY9+CAdA9Fk84NYBNM5+HoQ9ot4AU6GoZXWW7XnZknZLV9U8uu/lPHw5ky8pbfdsGLjcLhtUZCT0b+HLUrTctSg39uCPyLmDfy3zQUxk+cHMh1w012CQyyqZi0jvAIW155KPFFfE5U0QwKBgQDCdELgmObOINUg2KGbgQ/exFYXheMN+4ulkeV+BWbpb4kCrmO5GPFWaCvJsKHyihRbd+E2Q8oso7q/42IMpMRZwKIKsjyfi4+jud7Us44DCncOhiruNOt0vSdxWeW5t/iv8Ws8YGcCT0cLhHlfbFsH0e9GZiiJ0swZdAoQQUFPRQKBgAYlIqu7WK+/gWU6J5oJQj5B73/NE6eebns6rFvZm2kBtQZLl/KKg9T0nRrJB5JZfFcXnckdPJoRsorhvnS++sKXjrakds3RQS6DdeJEjTJWYxSfTVrAil60r4oXxE/VDQbvvQJs7tElNw4gLRv0UnSwFyOtFW95m3f26BoGdC8bAoGBALPF4nJcSb210vR78uSeLDrqTyGOMT0jGpbmzEF24w7bg9KLwTxF2BDnW9wqRX7Cs1FhAkA16frdH/D5WhOQXMsRcX3sDGoAD7rxQxZoLdmX2jv+REBXgns5yPZdgynw8KRQ0LBP+8vB2U/HmKJkvb/8EXeHSOEqlnDNu+QNQED1AoGAaXscACbyqCXUDacGfYfQ1+BQer5DN0vFAN0AvWM1pPfqSssESfEBvmncnv8YSKNLpL9uoaXxl37MJAMszZMPyM9CT8tNlJG3/tgmJPViztNPAVk5n3xceAMymJc4FnyGpuBqOj7g/x1MeeNtYmKX+QqqKeXLdY7bOaGlOUycNXQ=";
    // 支付宝公钥,查看地址：https://openhome.alipay.com/platform/keyManage.htm 对应APPID下的支付宝公钥。
    private  String alipay_public_key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3qOPreoCebB8DxN2uPdQRJ/Rm0Cx1xmFPP3Xjzpw6hXOhO5IojmrXbheNkGZiykTiTHYkSlGNxJyT+UHVby/gP2dK+uAht5FDonN+zaP9FdtfLpIfmnZbLrnc8kRJNcq/2WzFrbEz/4y6Qg7PdoOow74Y8AqZgxhpbV/sXKwnMXLvdLr3z+r/lvuBuOac7F7pQfmBIUsHVIm52+vQ7QNWM04mphP/AAOl5YtwaqxHDlecLF4JSQNnW/hR8HZHM7f1H8Bf8yVXr/XRxVbYVB8fTil62Ee1c9+vH5EpLbCWcKVm+3aRy/2EAyoNOwp4RxS4xo/lgLOXY5h8vPBMhGFYQIDAQAB";
    // 服务器[异步通知]页面路径  需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    // 支付宝会悄悄的给我们发送一个请求，告诉我们支付成功的信息
    private  String notify_url = "http://h3z3t7.natappfree.cc/payed/notify";

    // 页面跳转同步通知页面路径 需http://格式的完整路径，不能加?id=123这类自定义参数，必须外网可以正常访问
    //同步通知，支付成功，一般跳转到成功页
    private  String return_url = "http://member.gulimall.com/memberOrder.html";

    // 签名方式
    private  String sign_type = "RSA2";

    // 字符编码格式
    private  String charset = "utf-8";

    //订单超时时间
    private String timeout = "1m";

    // 支付宝网关； https://openapi.alipaydev.com/gateway.do
    private  String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

    public  String pay(PayVo vo) throws AlipayApiException {

        //AlipayClient alipayClient = new DefaultAlipayClient(AlipayTemplate.gatewayUrl, AlipayTemplate.app_id, AlipayTemplate.merchant_private_key, "json", AlipayTemplate.charset, AlipayTemplate.alipay_public_key, AlipayTemplate.sign_type);
        //1、根据支付宝的配置生成一个支付客户端
        AlipayClient alipayClient = new DefaultAlipayClient(gatewayUrl,
                app_id, merchant_private_key, "json",
                charset, alipay_public_key, sign_type);

        //2、创建一个支付请求 //设置请求参数
        AlipayTradePagePayRequest alipayRequest = new AlipayTradePagePayRequest();
        alipayRequest.setReturnUrl(return_url);
        alipayRequest.setNotifyUrl(notify_url);

        //商户订单号，商户网站订单系统中唯一订单号，必填
        String out_trade_no = vo.getOut_trade_no();
        //付款金额，必填
        String total_amount = vo.getTotal_amount();
        //订单名称，必填
        String subject = vo.getSubject();
        //商品描述，可空
        String body = vo.getBody();

        alipayRequest.setBizContent("{\"out_trade_no\":\""+ out_trade_no +"\","
                + "\"total_amount\":\""+ total_amount +"\","
                + "\"subject\":\""+ subject +"\","
                + "\"body\":\""+ body +"\","
                + "\"timeout_express\":\""+timeout+"\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");

        String result = alipayClient.pageExecute(alipayRequest).getBody();

        //会收到支付宝的响应，响应的是一个页面，只要浏览器显示这个页面，就会自动来到支付宝的收银台页面
        System.out.println("支付宝的响应："+result);

        return result;

    }
}

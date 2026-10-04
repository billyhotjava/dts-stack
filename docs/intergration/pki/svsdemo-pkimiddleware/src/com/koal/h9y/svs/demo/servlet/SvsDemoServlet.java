package com.koal.h9y.svs.demo.servlet;

import com.koal.common.util.Base64;
import com.koal.h9y.svs.demo.util.TestUtil;
import com.koal.svs.client.SvsClientException;
import com.koal.svs.client.SvsClientHelper;
import com.koal.svs.client.st.THostInfoSt;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author: niuc
 * @Date: 2022-03-02
 * @Description:
 */
public class SvsDemoServlet extends HttpServlet {

    private static final Integer MAX_WAIT_TIME = 5000;
    private static final boolean B_CIPHER = false;
    private static final Integer SOCKET_TIME_OUT = 1000;
    private static final Integer MIN_PORT = 1;
    private static final Integer MAX_PORT = 65535;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=utf-8");
        String signDataB64 = request.getParameter("signData");
        String originDataB64 = request.getParameter("originData");
        String certContentB64 = request.getParameter("certContent");
        String gwIP = request.getParameter("gwIP");
        Integer gwPort = Integer.parseInt(request.getParameter("gwPort"));


        if (isStrEmpty(signDataB64) || isStrEmpty(originDataB64)
                || isStrEmpty(certContentB64) ||  isStrEmpty(gwIP)) {
            sendResp(resp, "必填参数为空！");
            return;
        }
        if (null == gwPort || gwPort < MIN_PORT || gwPort > MAX_PORT) {
            sendResp(resp, "输入的端口不合法！");
            return;
        }


        System.out.println("签名数据:\n" + signDataB64
                + "\n原文数据:\n" + originDataB64 + "\n证书内容:\n" + certContentB64
                + "\n网关ip:" + gwIP + ", 网关端口:" + gwPort);

        String error = "";

        // 对接关键流程 START
        SvsClientHelper svsClientHelper = SvsClientHelper.getInstance();
        THostInfoSt tHostInfoSt = new THostInfoSt();

        //设置网关ip+端口
        svsClientHelper.initialize(gwIP, gwPort, MAX_WAIT_TIME, B_CIPHER, SOCKET_TIME_OUT);
        tHostInfoSt.setSvrIP(gwIP);
        tHostInfoSt.setPort(gwPort);

        int result = 0;

        // 注：签名原文需要传原文数据，而不是base64编码的数据
        byte[] arrayOriginData = Base64.decode(originDataB64);

        try {
            result = svsClientHelper.verifySign(-1, -1, arrayOriginData,
                    arrayOriginData.length, certContentB64, signDataB64, tHostInfoSt);
        }catch (SvsClientException e) {
            System.err.println("验签失败！" + e);
            error = "签名验签失败，" + e.getMessage();
            sendResp(resp, error);
            return;
        }
        // 对接关键流程 END

        String certCn = TestUtil.getCertInfo(certContentB64,0,"CN");

        if(result == 0){
            error = "签名验签成功,certCn:" + certCn;
        } else{
            error = "签名验签失败，错误码：" + result;
        }
        sendResp(resp, error);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    private void sendResp(HttpServletResponse resp, String msg) throws IOException {
        PrintWriter out = resp.getWriter();
        out.print(msg);
        out.close();
    }
    private static boolean isStrEmpty(String str) {
        if (null == str || str.length() == 0) {
            return true;
        }
        return false;
    }

}

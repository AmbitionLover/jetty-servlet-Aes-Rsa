package serves.impl;

import entity.MyFile;
import org.eclipse.jetty.server.Request;
import serves.MyFileServe;
import serves.UpAndDwServe;
import until.GetJson;
import until.RsaUtil;
import until.SymmetricEncoder;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * @author Ambi
 * @version 1.0
 * @date 2020/3/3 9:36
 */
//说明该Servlet处理的是multipart/form-data类型的请求
@MultipartConfig
public class UpAndDwServeImpl implements UpAndDwServe {


    //使用MultipartConfig需要加上这个配置，不加报MultipartConfig不存在
    private static final MultipartConfigElement MULTI_PART_CONFIG = new MultipartConfigElement("E:\\javacode\\SpringBoot\\test\\fileServe");
    private static final long serialVersionUID = 1L;
    private static final String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCqW2VX0YoBwhWvXKGCEui/r1FwsrTFRGdL/IKW+igpznK37AAB4PLQsXgHLP+tntFcLSUVxnB8q1mLEQRhdo+S01v7NxnjLrvmCHP6C+xTlIJw22zWmdAhPrmcKouVDM/TFvsPxuevv1MJzjUn36RzZmF8Jg6+0N0kA3M9k8LnWwIDAQAB";

    private MyFileServe myFileServe = new MyFileServeImpl();

    @Override
    public void file2Aes(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String contentType = request.getContentType();
        //判断是不是文件请求。必须加
        if(contentType != null && contentType.startsWith("multipart/")){
            /*  允许跨域访问 */
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "*");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.addHeader("Access-Control-Allow-Headers", "*");
            response.setHeader("Access-Control-Allow-Credentials", "*");
            //保存文件信息。存在在数据库中。
            MyFile file = new MyFile();
            //设置文件配置。必须加。
            request.setAttribute(Request.MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG);
            //说明输入的请求信息采用UTF-8编码方式
            request.setCharacterEncoding("utf-8");
            response.setContentType("text/html; charset=UTF-8");
            PrintWriter out = response.getWriter();
            //Servlet3.0中新引入的方法，用来处理multipart/form-data类型编码的表单

            //首先用RSA验证。
            if (!Aestototo(request)){
                return;
            }


            Part part = request.getPart("file");
            //获取HTTP头信息headerInfo=（form-data; name="file" filename="文件名"）
            String headerInfo = part.getHeader("content-disposition");
            System.out.println("文件大小："+part.getSize());
            file.setFileSize((int) part.getSize());
            //从HTTP头信息中获取文件名fileName=（文件名）
            String fileName = headerInfo.substring(headerInfo.lastIndexOf("=") + 2, headerInfo.length() - 1);
            file.setOldname(fileName);
            //获得存储上传文件的文件夹路径
            String fileSavingFolder = request.getServletContext().getRealPath("upload");
            //按理说这个获取upload的相对路径。我这不对。
            System.out.println(fileSavingFolder);
            if (fileSavingFolder==null){
                fileSavingFolder="upload";
            }
            //获取文件的后缀名
            String suffix = "."+ fileName.substring(fileName.lastIndexOf(".") + 1);
            file.setFiletype(suffix);
            //UUID
            String uuid = UUID.randomUUID().toString();
            file.setUid(uuid);

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            //获取当前日期
            String currentDate = dateFormat.format( new Date() );
            //获得存储上传文件的完整路径（文件夹路径+文件名）
            //文件夹位置固定，文件夹采用与上传文件的原始名字相同

            //加密文件统一用txt保存
            String fileSavingPath = fileSavingFolder + File.separator + currentDate + File.separator +uuid +".txt";
            System.out.println(fileSavingPath);
            file.setPath(fileSavingPath);
            //如果存储上传文件的文件夹不存在，则创建文件夹
            File f = new File(fileSavingFolder+File.separator + currentDate);
            if(!f.exists()){
                System.out.println("新建文件夹");
                f.mkdirs();
            }
            String absolutePath = f.getAbsolutePath();
            System.out.println(absolutePath);
            //将上传的文件内容写入服务器文件中
            /**
             * 下载运用AES加密
             */
            InputStream inputStream = part.getInputStream();
            byte[] getData = readInputStream(inputStream);
            inputStream.read(getData);
            String str = new String(getData);

//            System.out.println ("打印内容："+str);
            String content = SymmetricEncoder.AESEncode("123", str);
//            System.out.println(content);
            FileWriter fwriter = null;
            try {
                fwriter = new FileWriter(fileSavingPath);
                fwriter.write(content);
                //输出上传成功信息
                System.out.println("文件上传成功~！");
                myFileServe.saveFile(file);
                out.println(GetJson.ok("success",fileSavingPath.replaceAll( "\\\\",   "\\\\\\\\")));
            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                try {
                    fwriter.flush();
                    fwriter.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        } else {

        }
    }

    @Override
    public void dpwnloadFile(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        /*  允许跨域访问 */
        try{
            String uid = req.getParameter("uid");
            System.out.println(uid);
            MyFile myFile = myFileServe.findByUid(uid);
            System.out.println(myFile.getOldname());
            //服务器相对路径
            String filepath = myFile.getPath();
            System.out.println(filepath);

            /*打开文件，创建File类型的文件对象*/
            File file = new File(filepath);
            /*如果文件存在*/
            if(file.exists()){
                System.out.println("文件存在");

                /*获得文件名，并采用UTF-8编码方式进行编码，以解决中文问题*/
                String filename = URLEncoder.encode(file.getName(), "UTF-8");
                System.out.println(filename);
                /*重置response对象*/
                resp.reset();
                resp.setHeader("Access-Control-Allow-Origin", "*");
                resp.setHeader("Access-Control-Allow-Methods", "*");
                resp.setHeader("Access-Control-Max-Age", "3600");
                resp.addHeader("Access-Control-Allow-Headers", "*");
                resp.setHeader("Access-Control-Allow-Credentials", "*");
                resp.setCharacterEncoding("UTF-8");
                resp.setHeader("content-type","application/octet-stream;charset=UTF-8");
//                //3.2设置响应头打开方式：content-disposition
                resp.setHeader("content-disposition","attachment;filename="+filename);
                //设置HTTP头信息中内容
//                resp.addHeader("Content-Disposition","attachment:filename=\"" + filename + "\"" );
                //设置文件的长度
                int fileLength = (int)file.length();
                System.out.println(fileLength);
                resp.setContentLength(fileLength);
                /*如果文件长度大于0*/
                if(fileLength!=0){
                    //创建输入流
                    InputStream inStream = new FileInputStream(file);
                    byte[] buf = new byte[4096];
                    //创建输出流
                    ServletOutputStream servletOS = resp.getOutputStream();
                    int readLength;
                    //读取文件内容并写到response的输出流当中
                    while(((readLength = inStream.read(buf))!=-1)){
                        servletOS.write(buf, 0, readLength);
                    }
                    //关闭输入流
                    inStream.close();
                    //刷新输出缓冲
                    servletOS.flush();
                    //关闭输出流
                    servletOS.close();
                }
            }else {
                System.out.println("文件不存在~！");
                PrintWriter out = resp.getWriter();
                out.println("文件 \"" + myFile.getOldname() + "\" 不存在");
            }
        }catch(Exception e){
            System.out.println(e);
        }
    }

    /**
     * 读取内容header
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static  byte[] readInputStream(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[4096];
        int len = 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while((len = inputStream.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.close();
        return bos.toByteArray();
    }

    /**
     * 判断SID是否符合要求。
     * @param request
     * @return
     * @throws IOException
     * @throws ServletException
     */
    public boolean Aestototo(HttpServletRequest request) throws IOException, ServletException {

        //获取SID用户随机生成的字符串。
        Part partSID = request.getPart("X-SID");
        byte[] getSIDData = readInputStream(partSID.getInputStream());
        String SID = new String(getSIDData);

        //用户密钥加密后的内容
        Part partSignature = request.getPart("X-Signature");
        byte[] getSignatureData = readInputStream(partSignature.getInputStream());
        String Signature = new String(getSignatureData);

        //用公钥解锁。解锁后内容与SID比较
        String result = RsaUtil.deWithRSAPublicKey(Signature, publicKey);
        if (SID.equals(result)){
            System.out.println("验证成功");
            return true;
        }
        return false;
    }
}
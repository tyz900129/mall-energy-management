package com.wanyuecity.energy.access;

import com.wanyuecity.energy.core.BizException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HikvisionIsapiClient {
    private static final Pattern IPV4 = Pattern.compile("^(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");
    private static final Pattern CHALLENGE_VALUE = Pattern.compile("([a-zA-Z]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))");
    private static final DateTimeFormatter DEVICE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(5)).readTimeout(Duration.ofSeconds(8))
            .writeTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json;

    public HikvisionIsapiClient(ObjectMapper json) { this.json = json; }

    public DeviceInfo probe(String ip, int port, String username, String password) {
        if (ip == null || !IPV4.matcher(ip.trim()).matches()) throw BizException.badRequest("请输入正确的设备 IPv4 地址");
        if (port < 1 || port > 65535) throw BizException.badRequest("设备端口不正确");
        if (username == null || username.isBlank()) throw BizException.badRequest("设备用户名不能为空");
        if (password == null || password.isBlank()) throw BizException.badRequest("设备密码不能为空");
        try { return parse(execute(ip.trim(),port,username.trim(),password,"GET","/ISAPI/System/deviceInfo",null,"application/xml"), ip); }
        catch (BizException ex) { throw ex; } catch (Exception ex) { throw BizException.badRequest("无法连接门禁设备，请检查 IP、网络和设备状态"); }
    }

    public Map<String,Object> discover(String ip,int port,String username,String password) {
        DeviceInfo info=probe(ip,port,username,password);Map<String,Object>out=new LinkedHashMap<>();out.put("reachable",true);out.put("deviceName",info.deviceName());out.put("model",info.model());out.put("serialNumber",info.serialNumber());out.put("firmwareVersion",info.firmwareVersion());
        ProbeResult access=get(ip,port,username,password,"/ISAPI/AccessControl/capabilities");out.put("accessControlSupported",access.ok());
        ProbeResult events=get(ip,port,username,password,"/ISAPI/Event/capabilities");out.put("eventSubscriptionSupported",events.ok());
        ProbeResult personCapabilities=get(ip,port,username,password,"/ISAPI/AccessControl/UserInfo/capabilities?format=json");out.put("personCapabilityAvailable",personCapabilities.ok());
        ProbeResult faceCapabilities=get(ip,port,username,password,"/ISAPI/Intelligent/FDLib/capabilities?format=json");out.put("faceCapabilityAvailable",faceCapabilities.ok());
        ProbeResult faceRecordCapabilities=get(ip,port,username,password,"/ISAPI/Intelligent/FDLib/FaceDataRecord/capabilities?format=json");out.put("faceRecordCapabilityAvailable",faceRecordCapabilities.ok());
        ProbeResult idCardCapabilities=get(ip,port,username,password,"/ISAPI/AccessControl/IDCardInfo/capabilities?format=json");out.put("idCardCapabilityAvailable",idCardCapabilities.ok());
        try {String body="{\"UserInfoSearchCond\":{\"searchID\":\""+UUID.randomUUID()+"\",\"searchResultPosition\":0,\"maxResults\":1}}";byte[]bytes=execute(ip,port,username,password,"POST","/ISAPI/AccessControl/UserInfo/Search?format=json",body,"application/json");JsonNode root=json.readTree(bytes);JsonNode search=root.path("UserInfoSearch");out.put("personReadSupported",true);out.put("personTotal",search.path("totalMatches").asInt(search.path("numOfMatches").asInt(0)));JsonNode users=search.path("UserInfo");if(users.isArray()&&users.size()>0){JsonNode sample=users.get(0);List<String>fields=new ArrayList<>();sample.fieldNames().forEachRemaining(fields::add);out.put("personFields",fields);out.put("sampleFaceCount",sample.path("numOfFace").asInt(0));out.put("sampleCardCount",sample.path("numOfCard").asInt(0));List<String>extensionFields=new ArrayList<>();JsonNode extensions=sample.path("PersonInfoExtends");if(extensions.isArray())for(JsonNode extension:extensions)extension.fieldNames().forEachRemaining(name->{if(!extensionFields.contains(name))extensionFields.add(name);});out.put("personExtensionFields",extensionFields);out.put("certificateFieldDetected",hasAnyField(sample,"certificateNo","certificateType","idNumber","IDNumber","certNo"));String faceUrl=sample.path("faceURL").asText("");out.put("faceUrlReturned",!faceUrl.isBlank());if(!faceUrl.isBlank()){try{String faceUri=faceUrl.startsWith("http://")||faceUrl.startsWith("https://")?java.net.URI.create(faceUrl).getRawPath()+(java.net.URI.create(faceUrl).getRawQuery()==null?"":"?"+java.net.URI.create(faceUrl).getRawQuery()):faceUrl;byte[]image=execute(ip,port,username,password,"GET",faceUri,null,"image/jpeg,image/png,*/*");out.put("faceImageReadSupported",image.length>0);out.put("sampleFaceImageBytes",image.length);}catch(Exception ignored){out.put("faceImageReadSupported",false);}}}}catch(Exception ex){out.put("personReadSupported",false);out.put("personTotal",null);}
        ProbeResult cards=get(ip,port,username,password,"/ISAPI/AccessControl/CardInfo/Count?format=json");out.put("cardReadSupported",cards.ok());if(cards.ok())try{JsonNode n=json.readTree(cards.body());out.put("cardTotal",firstInt(n,"cardInfoNumber","totalMatches","count"));}catch(Exception ignored){}
        out.put("readOnly",true);out.put("probedAt",java.time.LocalDateTime.now().toString());return out;
    }

    private boolean hasAnyField(JsonNode node,String...names){for(String name:names)if(node.findValue(name)!=null)return true;return false;}

    public PersonPage readPersonPage(String ip,int port,String username,String password,String searchId,int position,int size) {
        try {String body="{\"UserInfoSearchCond\":{\"searchID\":\""+searchId+"\",\"searchResultPosition\":"+position+",\"maxResults\":"+size+"}}";JsonNode search=json.readTree(execute(ip,port,username,password,"POST","/ISAPI/AccessControl/UserInfo/Search?format=json",body,"application/json")).path("UserInfoSearch");List<Map<String,Object>>rows=new ArrayList<>();JsonNode users=search.path("UserInfo");if(users.isArray())for(JsonNode user:users)rows.add(json.convertValue(user,Map.class));return new PersonPage(search.path("totalMatches").asInt(rows.size()),rows);
        } catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("读取设备人员分页失败："+position);}
    }

    public CardPage readCardPage(String ip,int port,String username,String password,String searchId,int position,int size) {
        try {String body="{\"CardInfoSearchCond\":{\"searchID\":\""+searchId+"\",\"searchResultPosition\":"+position+",\"maxResults\":"+size+"}}";JsonNode search=json.readTree(execute(ip,port,username,password,"POST","/ISAPI/AccessControl/CardInfo/Search?format=json",body,"application/json")).path("CardInfoSearch");List<Map<String,Object>>rows=new ArrayList<>();JsonNode cards=search.path("CardInfo");if(cards.isArray())for(JsonNode card:cards)rows.add(json.convertValue(card,Map.class));return new CardPage(search.path("totalMatches").asInt(rows.size()),rows);
        } catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("读取设备卡片分页失败："+position);}
    }

    public byte[] readFaceImage(String ip,int port,String username,String password,String faceUrl){
        if(faceUrl==null||faceUrl.isBlank())throw BizException.notFound("设备没有返回该人员的人脸照片");
        try{java.net.URI parsed=java.net.URI.create(faceUrl);String uri=(parsed.isAbsolute()?parsed.getRawPath():faceUrl)+(parsed.isAbsolute()&&parsed.getRawQuery()!=null?"?"+parsed.getRawQuery():"");return execute(ip,port,username,password,"GET",uri,null,"image/jpeg,image/png,*/*");}
        catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("读取设备人脸照片失败");}
    }

    public void createPerson(String ip,int port,String username,String password,String employeeNo,String name,LocalDateTime from,LocalDateTime to,byte[]faceImage){
        if(employeeNo==null||!employeeNo.matches("[A-Za-z0-9_-]{1,32}"))throw BizException.badRequest("设备人员编号格式不正确");
        if(name==null||name.isBlank()||name.length()>32)throw BizException.badRequest("设备人员姓名格式不正确");
        String step="创建人员";try{
            String begin=from.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),end=to.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
            Map<String,Object>valid=Map.of("enable",true,"beginTime",begin,"endTime",end,"timeType","local");
            Map<String,Object>user=new LinkedHashMap<>();user.put("employeeNo",employeeNo);user.put("name",name);user.put("userType","normal");user.put("Valid",valid);user.put("doorRight","1");user.put("RightPlan",List.of(Map.of("doorNo",1,"planTemplateNo","1")));
            execute(ip,port,username,password,"POST","/ISAPI/AccessControl/UserInfo/Record?format=json",json.writeValueAsString(Map.of("UserInfo",user)),"application/json");
            step="上传人脸";
            String meta=json.writeValueAsString(Map.of("faceLibType","blackFD","FDID","1","FPID",employeeNo));
            RequestBody multipart=new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("FaceDataRecord",null,RequestBody.create(meta,MediaType.get("application/json; charset=utf-8")))
                    .addFormDataPart("FaceImage","face.jpg",RequestBody.create(faceImage,MediaType.get("image/jpeg"))).build();
            executeBody(ip,port,username,password,"POST","/ISAPI/Intelligent/FDLib/FaceDataRecord?format=json",multipart,"application/json");
            step="写入后校验";
            if(!personExists(ip,port,username,password,employeeNo,true))throw BizException.badRequest("设备写入后校验失败");
        }catch(BizException ex){throw ex;}catch(Exception ex){String detail=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getClass().getSimpleName()+"："+ex.getMessage();throw BizException.badRequest(step+"失败（"+detail.substring(0,Math.min(300,detail.length()))+"）");}
    }

    public void deletePerson(String ip,int port,String username,String password,String employeeNo){
        try{String body=json.writeValueAsString(Map.of("UserInfoDelCond",Map.of("EmployeeNoList",List.of(Map.of("employeeNo",employeeNo)))));execute(ip,port,username,password,"PUT","/ISAPI/AccessControl/UserInfo/Delete?format=json",body,"application/json");if(personExists(ip,port,username,password,employeeNo,false))throw BizException.badRequest("设备删除后校验失败");}
        catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("从设备删除人员失败");}
    }

    public void updatePersonValidity(String ip,int port,String username,String password,String employeeNo,String name,LocalDateTime from,LocalDateTime to){
        try{
            Map<String,Object> valid=new LinkedHashMap<>();valid.put("enable",true);valid.put("beginTime",DEVICE_TIME.format(from));valid.put("endTime",DEVICE_TIME.format(to));valid.put("timeType","local");
            Map<String,Object> user=new LinkedHashMap<>();user.put("employeeNo",employeeNo);user.put("name",name);user.put("userType","normal");user.put("Valid",valid);
            execute(ip,port,username,password,"PUT","/ISAPI/AccessControl/UserInfo/Modify?format=json",json.writeValueAsString(Map.of("UserInfo",user)),"application/json");
            if(!personExists(ip,port,username,password,employeeNo,false))throw BizException.badRequest("设备续期后校验失败");
        }catch(BizException ex){throw ex;}catch(Exception ex){throw BizException.badRequest("更新设备人员有效期失败");}
    }

    public boolean personExists(String ip,int port,String username,String password,String employeeNo,boolean requireFace){
        try{String body=json.writeValueAsString(Map.of("UserInfoSearchCond",Map.of("searchID",UUID.randomUUID().toString(),"searchResultPosition",0,"maxResults",10,"EmployeeNoList",List.of(Map.of("employeeNo",employeeNo)))));JsonNode users=json.readTree(execute(ip,port,username,password,"POST","/ISAPI/AccessControl/UserInfo/Search?format=json",body,"application/json")).path("UserInfoSearch").path("UserInfo");if(users.isArray())for(JsonNode user:users)if(employeeNo.equals(user.path("employeeNo").asText()))return !requireFace||user.path("numOfFace").asInt(0)>0;return false;}catch(Exception ex){throw BizException.badRequest("设备人员校验失败");}
    }

    private ProbeResult get(String ip,int port,String username,String password,String uri){try{return new ProbeResult(true,execute(ip,port,username,password,"GET",uri,null,"application/xml"));}catch(Exception ex){return new ProbeResult(false,new byte[0]);}}
    private byte[] execute(String ip,int port,String username,String password,String method,String uri,String body,String accept) throws Exception {RequestBody rb=body==null?null:RequestBody.create(body,MediaType.get("application/json; charset=utf-8"));return executeBody(ip,port,username,password,method,uri,rb,accept);}
    private byte[] executeBody(String ip,int port,String username,String password,String method,String uri,RequestBody body,String accept) throws Exception {String url="http://"+ip+":"+port+uri;Request.Builder builder=new Request.Builder().url(url).header("Accept",accept);if("POST".equals(method))builder.post(body);else if("PUT".equals(method))builder.put(body);else if("DELETE".equals(method))builder.delete(body);else builder.get();Request initial=builder.build();try(Response first=http.newCall(initial).execute()){if(first.isSuccessful())return first.body()==null?new byte[0]:first.body().bytes();if(first.code()!=401){String detail=first.body()==null?"":first.body().string();throw deviceError(first.code(),detail);}String auth=authorization(first.header("WWW-Authenticate",""),username,password,method,uri);try(Response response=http.newCall(initial.newBuilder().header("Authorization",auth).build()).execute()){if(response.code()==401||response.code()==403)throw BizException.badRequest("设备用户名或密码不正确");if(!response.isSuccessful()){String detail=response.body()==null?"":response.body().string();throw deviceError(response.code(),detail);}return response.body()==null?new byte[0]:response.body().bytes();}}}
    private BizException deviceError(int code,String detail){if(detail.contains("SubpicAnalysisModelingError")||detail.contains("PicFeaturePoints"))return BizException.badRequest("设备无法从人脸照片提取有效特征，请重新上传清晰、正面、无遮挡且仅包含一人的现场照片");return BizException.badRequest("设备接口返回 HTTP "+code+" "+detail);}
    private Integer firstInt(JsonNode node,String...names){for(String name:names){JsonNode found=node.findValue(name);if(found!=null&&found.canConvertToInt())return found.asInt();}return null;}

    private String authorization(String challenge, String username, String password, String method, String uri) throws Exception {
        if (challenge.regionMatches(true, 0, "Basic", 0, 5)) {
            return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }
        if (!challenge.regionMatches(true, 0, "Digest", 0, 6)) throw BizException.badRequest("设备不支持可识别的登录认证方式");
        Map<String,String> values = new LinkedHashMap<>();
        Matcher matcher = CHALLENGE_VALUE.matcher(challenge.substring(6));
        while (matcher.find()) values.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
        String realm = values.get("realm"), nonce = values.get("nonce");
        if (realm == null || nonce == null) throw BizException.badRequest("设备登录认证信息不完整");
        String qop = values.getOrDefault("qop", "").toLowerCase(Locale.ROOT).contains("auth") ? "auth" : "";
        String nc = "00000001", cnonce = UUID.randomUUID().toString().replace("-", "").substring(0,16);
        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        String digest = qop.isBlank() ? md5(ha1 + ":" + nonce + ":" + ha2) : md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
        StringBuilder out = new StringBuilder("Digest username=\"").append(escape(username)).append("\", realm=\"").append(escape(realm)).append("\", nonce=\"").append(escape(nonce)).append("\", uri=\"").append(uri).append("\", response=\"").append(digest).append("\"");
        if (!qop.isBlank()) out.append(", qop=").append(qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        if (values.get("opaque") != null) out.append(", opaque=\"").append(escape(values.get("opaque"))).append("\"");
        if (values.get("algorithm") != null) out.append(", algorithm=").append(values.get("algorithm"));
        return out.toString();
    }

    private DeviceInfo parse(byte[] xml, String ip) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String serial = value(document,"serialNumber");
        if (serial.isBlank()) throw BizException.badRequest("设备已连接，但没有返回序列号");
        return new DeviceInfo(value(document,"deviceName"), value(document,"model"), serial,
                value(document,"firmwareVersion"), value(document,"firmwareReleasedDate"),
                value(document,"deviceID"), value(document,"macAddress"), ip);
    }

    private String value(Document d, String tag) { NodeList n=d.getElementsByTagNameNS("*",tag); if(n.getLength()==0)n=d.getElementsByTagName(tag); return n.getLength()==0?"":n.item(0).getTextContent().trim(); }
    private String md5(String value) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.ISO_8859_1))); }
    private String escape(String value) { return value.replace("\\","\\\\").replace("\"","\\\""); }
    public record DeviceInfo(String deviceName,String model,String serialNumber,String firmwareVersion,String firmwareReleasedDate,String deviceId,String macAddress,String ipAddress) {}
    public record PersonPage(int total,List<Map<String,Object>> rows) {}
    public record CardPage(int total,List<Map<String,Object>> rows) {}
    private record ProbeResult(boolean ok,byte[] body) {}
}

package ge.kadri.tv;

import java.net.*;
import java.io.UnsupportedEncodingException;

final class DeepLinks {
    static final String HOME="kadritv://home";
    static String movie(String url){try{return "kadritv://movie?url="+URLEncoder.encode(Repository.pageUrl(url),"UTF-8");}catch(UnsupportedEncodingException e){throw new AssertionError(e);}}
    static String movieUrl(String raw){
        if(raw==null||raw.length()>8192)return null;
        URI uri=URI.create(raw);
        if(!"kadritv".equals(uri.getScheme())||!"movie".equals(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)return null;
        String query=uri.getRawQuery();if(query==null)return null;
        String found=null;
        for(String part:query.split("&"))if(part.startsWith("url=")){
            if(found!=null)throw new IllegalArgumentException("ერთი ფილმის ბმულია საჭირო.");
            try{found=Repository.pageUrl(URLDecoder.decode(part.substring(4),"UTF-8"));}catch(UnsupportedEncodingException e){throw new AssertionError(e);}
        }
        return found;
    }
}

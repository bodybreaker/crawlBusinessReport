import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Dart {

    private static final String DART_URL = "http://dart.fss.or.kr";
    private static final String DOWNLOAD_PAGE_PATH = "/pdf/download/main.do?";
    private static final String SEARCH_RESULT_CSS_QUERY = "a[title=사업보고서 공시뷰어 새창]";
    private static final String SEARCH_RESULT_CSS_QUERY_GAMSA = "a[title=감사보고서 공시뷰어 새창]";
    private static final String REPORT_CSS_QUERY ="a[href=#download]";
    private static final String DOWNLOAD_CSS_QUERY ="table tr";

    /*
        업종 데이터 - 코드 파싱
    */

    public List<BusinessModel> getCodes(String path) throws IOException {
        List<BusinessModel> codeList = new ArrayList<>();

        InputStream is = getClass().getResourceAsStream(path);
        InputStreamReader isr = new InputStreamReader(is);
        BufferedReader reader = new BufferedReader(isr);
        String line;

        while ((line = reader.readLine()) != null)
        {
            String[] parts = line.split(":", 2);
            if (parts.length >= 2)
            {
                String code = parts[0];
                String name = parts[1];

                codeList.add(new BusinessModel(code,name));

            } else {
                log.info("ignoring line: " + line);
            }
        }
        return codeList;
    }

    /*
        Step1. Post - http://dart.fss.or.kr/dsab002/search.ax
         data :
                currentPage: 1
                maxResults: 15
                maxLinks: 10
                sort: date
                series: desc
                textCrpCik:
                reportNamePopYn:
                textCrpNm:
                textPresenterNm:
                startDate: 20200214
                endDate: 20200814
                finalReport: recent
                typesOfBusiness: 0103 [업종코드]
                corporationType: all
                closingAccountsMonth: 12 [결산월]
                reportName:
                publicType: A001 [ A001 -> 사업보고서 / F001 -> 감사보고서 ]

         return 보고서 페이지 URLs
     */
    public List<String> doSearch(String typeOfBusiness,String publicType) throws Exception {

        Map searchMap = new HashMap<String,String>();
        searchMap.put("currentPage","1");
        searchMap.put("maxResults","100");
        searchMap.put("maxLinks","10");
        searchMap.put("sort","date");
        searchMap.put("series","desc");
        searchMap.put("textCrpCik","");
        searchMap.put("reportNamePopYn","");
        searchMap.put("textCrpNm","");
        searchMap.put("textPresenterNm","");
        searchMap.put("startDate","20200216"); // 검색시작 일자
        searchMap.put("endDate","20200816"); // 검색종료 일자
        searchMap.put("finalReport","recent");
        searchMap.put("typesOfBusiness",typeOfBusiness); // 업종코드
        searchMap.put("corporationType","all");
        searchMap.put("closingAccountsMonth","12"); //결산월
        searchMap.put("reportName","");
        searchMap.put("publicType","F001"); //[ A001 -> 사업보고서 / F001 -> 감사보고서 ]

        log.info("::"+searchMap);

        Connection.Response response = Jsoup.connect("http://dart.fss.or.kr/dsab002/search.ax")
                .method(Connection.Method.POST)
                .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0") // 봇 차단 방지 user agent
                .data(searchMap)
                .execute();
        Document resDoc = response.parse();

        //log.info(resDoc);

        Elements urlElements = resDoc.select(SEARCH_RESULT_CSS_QUERY_GAMSA);// css 쿼리
        if(urlElements == null){
            throw new Exception("검색결과없음");
        }

        log.info("검색결과 길이 : "+ urlElements.size());

        List<String> urlList = new ArrayList<String>();

        urlElements.forEach(x->{
            //log.info(x.attr("href"));
            String url = DART_URL + x.attr("href");
        urlList.add(url);
    });

        return urlList;
}

    /*
        Step2. 보고서 창에서 다운로드 페이지 파싱
        a[href=#download] 결과 ::: <a href="#download" onclick="openPdfDownload('20200722000337', '7416501'); return false;"> 에서
        두번째 파라미터 가져오기

        return 다운로드 페이지 urls
     */
    public List<String> getDonwloadURLs(List<String> reportURLs){

        // 각각의 보고서창에서 다운로드 URL 추출

        List<String> downloadURLs = new ArrayList<>();
        reportURLs.forEach(url->{
            if(url!=null){
                try {
                    Document reportDoc = Jsoup.connect(url).get();
                    Element el = reportDoc.selectFirst(REPORT_CSS_QUERY);
                    String jsAttr = el.attr("onclick"); // "openPdfDownload('20200722000337', '7416501')
                    /*
                     function openPdfDownload(rcpNo, dcmNo) {
                            var size = getOpenSize(530, 480);
                            var url = "/pdf/download/main.do?rcp_no=" + rcpNo + "&dcm_no=" + dcmNo;
                            var win = window.open( url, 'DOWNLOAD', ''+size+', resizable=no, status=no, scrollbars=yes');
                            if(win == null){
                                    alert("팝업차단을 해제해주세요.");
                            }else{
                                    win.focus();
                        }
                     */
                    Pattern pattern = Pattern.compile("'(.*?)'");
                    Matcher matcher = pattern.matcher(jsAttr);

                    int matchCnt = 0;
                    while (matcher.find()){
                        matchCnt++;
                    }

                    if(matchCnt==2){
                        matcher.reset();
                        String rcpNo="";
                        String dcmNo="";
                        for(int i=0; i< 2; i++){
                            matcher.find();
                            if(i==0) rcpNo = matcher.group().replaceAll("'","");
                            else dcmNo = matcher.group().replaceAll("'","");
                        }

                        String downloadUrl = DART_URL+DOWNLOAD_PAGE_PATH+"rcp_no="+rcpNo+"&"+"dcm_no="+dcmNo;
                        log.info(downloadUrl);
                        downloadURLs.add(downloadUrl);
                    }else{
                        System.err.println("매개변수 부족 >> " + jsAttr);
                    }

                } catch (Exception e) {
                    System.err.println("리포트 파싱 에러 >>" + url);
                    e.printStackTrace();
                }

            }
            try {
                Thread.sleep(1000); // 디도스 공격 탐지 방지
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        return downloadURLs;
    }


    /*
        Step3. 테이블에서 해당 파일 파싱하여 다운로드

        @param : 다운로드페이지 URL , 다운로드 경로 , 다운로드할 파일의 이름

        table tr td >>  첫번째 td : 파일 명  , 두번째 td : 파일 다운로드 링크
     */

    public void downloadReport(String downloadURL, String downloadPath,String name) throws IOException {
        Document reportDoc = Jsoup.connect(downloadURL).get();
        Elements els = reportDoc.select(DOWNLOAD_CSS_QUERY);

        for(int i=0; i< els.size() ;i ++){
            Element el = els.get(i);
            if(el.html().contains(name)){ // 첫 번째 매칭건에 대해 다운로드
                //log.info(el.html());


                String fileName = el.selectFirst("td").text();

                Element linkEl = el.selectFirst("a"); //다운로드 링크만
                String downloadLink = DART_URL + linkEl.attr("href");
                log.info(fileName + " : " +downloadLink);

                Connection.Response res = Jsoup.connect(downloadLink)
                        .ignoreContentType(true)
                        .userAgent("Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:25.0) Gecko/20100101 Firefox/25.0") // 봇 차단 방지 user agent
                        .execute();

                //File
                FileOutputStream out = (new FileOutputStream(new File(downloadPath+"/"+fileName)));
                out.write(res.bodyAsBytes());
                out.close();

                try {
                    Thread.sleep(1000); // 디도스 공격 탐지 방지
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                break;
            }
        }


    }

}

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
public class Main {

    private static final String FOLDER_PATH ="download/";

    public static void main(String[] args) {

        Dart dart = new Dart();

        // 업종 데이터 모델 로드
        List<BusinessModel> businessModelList = null;
        try {
            businessModelList = dart.getCodes("/typesOfBusiness");
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        makeFolder(FOLDER_PATH); // download 폴더 생성

        // 다건 한꺼번에
        for (BusinessModel businessModel : businessModelList) {
            log.info("=====================================");
            log.info(businessModel.getName());
            log.info(businessModel.getCode());

            String saveDir = FOLDER_PATH+businessModel.getName();
            makeFolder(saveDir);

            // 다트 사이트 접속하여 검색 후 URL 리스트 가져옴
            List<String> reportUrlList = null;
            try {
                reportUrlList = dart.doSearch(businessModel.getCode(),null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if(reportUrlList == null) return;

            log.info("총 개수 :: "+ reportUrlList.size());
            log.info("=====================================\n\n");

            // 보고서 창에서 다운로드 페이지 URL 추출
            List<String> donwloadURLs = dart.getDonwloadURLs(reportUrlList);

            if(donwloadURLs!=null && donwloadURLs.size()>0){

                donwloadURLs.forEach(downloadURL->{
                    try {
                        dart.downloadReport(downloadURL,saveDir,"감사보고서");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

            }


       }

    }


    private static void makeFolder(String path){
        File Folder = new File(path);

        if (!Folder.exists()) {
            try{
                Folder.mkdir();
            }
            catch(Exception e){
                e.getStackTrace();
            }
        }
    }
}


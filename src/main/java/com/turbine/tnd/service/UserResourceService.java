package com.turbine.tnd.service;

import com.turbine.tnd.bean.*;
import com.turbine.tnd.dao.*;
import com.turbine.tnd.dto.*;
import com.turbine.tnd.utils.Filter;
import com.turbine.tnd.utils.FilterFactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Turbine
 * @Description:
 * @date 2023/2/16 16:36
 */

@Service
@Slf4j
@SuppressWarnings("all")
public class UserResourceService {
    @Autowired
    UserDao udao;
    @Autowired
    FolderDao fdao;
    @Autowired
    FilterFactor filterFactor;
    @Autowired
    SimpleFileService fs;
    @Autowired
    @Qualifier("EruptUploadStrategy")
    SliceFileService supload;
    @Autowired
    ResourceDao rdao;
    @Autowired
    ResourceRecycleDao rrdao;
    @Autowired
    UserResourceDao urdao;
    @Autowired
    UserShareResourceDao usrdao;


    @Value("${file.upload.tmpDir}")
    String tempDir;
    @Value("${file.upload.baseDir}")
    String baseDir;
    /**
     * @param multipartFile
     * @param userName
     * @return
     */
    public boolean simpleUpload(MultipartFile multipartFile, String userName, Message message, int parentId) {

        String originalName = multipartFile.getOriginalFilename();
        Filter<String> filter = filterFactor.getResource(FilterFactor.filterOpt.AC_FILTER);
        int idx = originalName.lastIndexOf(".");
        String suffix = originalName.substring(idx);
        String fileName = filter.filtration(originalName.substring(0, idx));

        //????????????id ?????????id
        User user = udao.inquireByName(userName);
        ResourceType type = fs.inquireType(suffix);
        if (type == null) {
            message.setResultCode(ResultCode.ERROR_400);
            message.setMessage("????????????????????????");
            return false;
        }

        return fs.upload(multipartFile, fileName, user.getId(), type.getId(), suffix, parentId);
    }


    /**
     * ????????????dto
     *
     * @param fudto
     * @return
     */
    public Message sliceUpload(FileRequestDTO fudto) throws IOException {
        //
        Message message = new Message(ResultCode.ERROR_500);
        Resource resource = null;
        User user = udao.inquireByName(fudto.getUserName());

        if (!supload.isSupport(fudto.getOriginalName())) {
            message.setResultCode(ResultCode.ERROR_400);
            message.setMessage("????????????????????????");
            FileUploadDTO fdto = new FileUploadDTO();
            fdto.setAccomplish(false);
            fdto.setAllSuccess(false);
            log.debug(fudto.getOriginalName() + "????????????????????????");
            message.setData(fdto);
        }else if ((resource = rdao.inquireByName(fudto.getFileName())) != null) {
            message.setResultCode(ResultCode.SUCCESS);
            FileUploadDTO fdto = new FileUploadDTO();
            fdto.setChunkNum(fudto.getChunkNum());
            fdto.setAccomplish(true);
            fdto.setAllSuccess(true);
            supload.deleteTempFile(fudto);

            int idx = fudto.getOriginalName().lastIndexOf(".");
            String originalName = fudto.getOriginalName().substring(0,idx);
            UserResource ur = new UserResource(user.getId(),resource.getId(),fudto.getFileName(), originalName, fudto.getParentId(), resource.getType_id());
            ur.setUploadTime(new Date(System.currentTimeMillis()));
            urdao.addUserResource(ur);//????????????????????????

            ResourceDTO dto = new ResourceDTO(ur);
            dto.setFileType( fudto.getOriginalName().substring(idx));
            dto.setFileSize(fudto.getTotalSize());

            fdto.setResource(dto);
            log.debug("??????: "+fudto.getFileName()+"   ???????????????????????????");
            message.setData(fdto);
        }else {
            //?????????????????????
            FileUploadDTO fdto = supload.sliceUpload(fudto);
            if(fdto != null){
                message.setData(fdto);
                message.setResultCode(ResultCode.SUCCESS);
            }
        }


        return message;
    }

    /**
     * ????????????
     *
     * @param fdto
     * @return
     */
    public FileDownLoadDTO sliceDownload(FileRequestDTO fdto) {

        return supload.sliceDownload(fdto);
    }



    /**
     * @Description: ???????????????????????????????????????
     * @author Turbine
     * @param
     * @param parentId ???????????????id
     * @return com.turbine.tnd.dto.ResourceFolder
     * @date 2023/2/9 19:43
     */
    public ResourceFolder getLevelResource(Integer parentId){
        Folder folder = fdao.inquireFolderById(parentId);
        ResourceFolder rfdto = new ResourceFolder();
        if (folder != null){
            List<FolderDTO> folders = udao.inquireUserFolders(parentId,folder.getUserId(), false,null);
            List<ResourceDTO> resources = rdao.inquireUserResourceByParentId(parentId,folder.getUserId(), false,null);
            rfdto.setFolders(folders);
            rfdto.setResources(resources);
        }

        return  rfdto;
    }
    // ??????????????????????????????
    public ResourceFolder getOwnLevelResource(Integer parentId,String userName,Boolean collect){
        User user = udao.inquireByName(userName);
        ResourceFolder rfdto = new ResourceFolder();
        List<FolderDTO> folders = udao.inquireUserFolders(parentId,user.getId(), false,collect);
        List<ResourceDTO> resources = rdao.inquireUserResourceByParentId(parentId,user.getId(), false,collect);
        rfdto.setFolders(folders);
        rfdto.setResources(resources);

        return  rfdto;
    }

    /**
     * ????????????md5 id ????????????????????????
     * @param userName  ?????????
     * @param fileName  ????????????md5??????
     * @param parentId  ?????????id
     * @return
     */
    public boolean ResourceIsExist(String userName, String fileName, Integer parentId) {
        boolean flag = false;
        User user = udao.inquireByName(userName);
        if (user != null) {
            if (rdao.hasResource(user.getId(), fileName,parentId) > 0) flag = true;
        }

        return flag;
    }

    /**
     * ????????????id ?????????????????????????????????
     * @param resourceId    ??????id
     * @param userName      ?????????
     * @return
     */
    public boolean hasResource(int resourceId, String userName) {
        boolean flag = false;

        User user = udao.inquireByName(userName);
        if(user != null){
            if(urdao.countUserResource(resourceId,user.getId()) > 0 )flag = true;
        }


        return flag;
    }

    /**
     * @Description: ?????????????????????????????????
     * @author Turbine
     * @param
     * @param userResourceId
     * @param userId
     * @return boolean
     * @date 2023/1/13 12:22
     */
    public boolean hasResource(int userResourceId, Integer userId) {
        return urdao.inquireUserResourceById(userResourceId).getU_id() == userId ;
    }

    //???????????????
    public Message mkdirFolder(int parentId, String userName, String folderName) {
        Message message = new Message();
        User user = udao.inquireByName(userName);
        Folder folder = new Folder(new Date(System.currentTimeMillis()), folderName, user.getId(), parentId);

        if (user != null && udao.addUserFolder(folder) > 0) {
            FolderDTO folderDTO = new FolderDTO();
            folderDTO.setFolderId(folder.getFolderId());
            folderDTO.setFolderName(folderName);
            folderDTO.setCreateTime(folder.getCreateTime());

            message.setResultCode(ResultCode.SUCCESS);
            message.setData(folderDTO);
        } else {
            message.setResultCode(ResultCode.ERROR_500);
        }
        return message;
    }


    public Resource inquireResource(Integer resourceId) {
        UserResource userResource = urdao.inquireUserResourceById(resourceId);


        return rdao.inquireByName(userResource.getFileName());
    }





    /**
     * @Description:     ?????????????????????????????????????????????????????????????????????
     * @author Turbine
     * @param
     * @param resourceId ????????????id or  ?????????id
     * @param type       ???????????????????????? ???0 ???????????? 1 ?????????
     * @param resp       ????????????????????????
     * @param userId     ????????????ID
     * @return void
     * @date 2023/2/4 15:51
     */
    public void getUResource(Integer resourceId, Integer type, HttpServletResponse resp, Integer userId) throws IOException {
        OutputStream os = resp.getOutputStream();
        if(type == 1){
            UserResource ur = urdao.inquireUserResourceById(resourceId);
            if(ur != null){
                Resource resource = rdao.inquireByName(ur.getFileName());
                File file = new File(resource.getLocation());
                int read = 0;
                byte[] data = new byte[1024];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));

                while((read = bis.read(data)) != -1){
                    os.write(data,0,read);
                }
                os.flush();
                if("mp4".equals(resource.getType().getType())){
                    resp.setContentType("video/mp4;charset=UTF-8");
                }
                resp.addHeader("Content-Disposition","attchement;filename=" +resource.getFileName()+resource.getType().getType());
            }
        }else if(type == 0){
            Folder folder = fdao.inquireFolderById(resourceId);
            resp.addHeader("Content-Disposition","attchement;filename=" +folder.getFolderName()+".zip");
            getUFolder(resourceId,userId,os);

        }

    }


    /**
     * @Description:        ??????????????????????????????????????????????????????
     * @author Turbine
     * @param
     * @param folderId      ???????????????id
     * @param userId        ?????????????????????
     * @param os            ????????????????????????
     * @return void
     * @date 2023/2/4 16:08
     */
    private void getUFolder(Integer folderId,Integer userId,OutputStream os){
        Folder f = fdao.inquireFolderById(folderId);

        File zipF = new File(baseDir+File.separator+tempDir + File.separator + f.getFolderName() + ".zip");
        try(ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipF)));
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(zipF))
        ) {
            zos.setMethod(ZipOutputStream.DEFLATED);//zip??????

            buildCompressFolder(zos,folderId,userId,"");
            zos.flush();//flush ???????????? ?????????????????????????????????????????????????????????

            byte[] data = new byte[1024];
            int read = 0;
            BufferedOutputStream bos = new BufferedOutputStream(os);
            while( (read = bis.read(data) ) != -1){
                bos.write(data,0,read);
            }
            bos.flush();

            zipF.deleteOnExit();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            log.debug("?????????????????????"+"getShareFolder");
            e.printStackTrace();
        }finally {

        }
    }

    /*
     * ??????????????????????????????
     * @param createTime
     * @param survivalTime  ??????????????????????????????
     * @return
             */
    boolean resourceIsExpire(Timestamp createTime, Integer survivalTime) {
        long time = createTime.getTime();
        long now = System.currentTimeMillis();

        return (time+(long)survivalTime*60*1000)-now < 0;
    }


    //???????????????????????????

    /**
     * @Description: ????????????????????????????????????????????????????????????????????????????????????
     * @author Turbine
     * @param
     * @param param
     * @return com.turbine.tnd.bean.Message
     * @date 2023/1/15 14:34
     */
    public Message inquireSliceProgress(FileRequestDTO param) {
        Message message = new Message(ResultCode.SUCCESS);

        if (!fs.hasExist(param.getFileName())) {
            List<Integer> list = supload.checkFinished(param);

            message.setData(list.stream().mapToInt(Integer::intValue).toArray());
        } else {
            FileUploadDTO fdto = new FileUploadDTO();
            fdto.setAccomplish(true);
            fdto.setAllSuccess(true);

            message.setData(fdto);
        }

        return message;
    }


    /**
     * @param fileId
     * @param newName
     * @return
     */
    public Message modifyFileName(String fileId, String newName) {
        Message message = new Message();
        if (udao.modifyFileName(fileId, newName) > 0) message.setResultCode(ResultCode.SUCCESS);
        else message.setResultCode(ResultCode.ERROR_500);

        return message;
    }

    public boolean FolderIsExist(int folderId) {
        return fdao.inquireFolderById(folderId) != null ;
    }

    public boolean hasFolder(int folderId , int userId) {
        return fdao.inquireFolder(folderId,userId) != null ;
    }

    //?????????????????????
    public Message modifyFolder(Folder folder, String userName) {
        Message message = new Message(ResultCode.ERROR_500);
        User user = udao.inquireByName(userName);
        if (user != null) {
            folder.setUserId(user.getId());
            if (fdao.modifyFolder(folder) > 0) message.setResultCode(ResultCode.SUCCESS);
        }

        return message;
    }


    //??????????????????
    public Message modifyUserResource(UserResource uResource, String userName) {
        Message message = new Message(ResultCode.ERROR_500);
        User user = udao.inquireByName(userName);
        if (user != null) {
            uResource.setU_id(user.getId());
            UserResource ur = urdao.inquireUserResourceById(uResource.getId());
            if(ur != null){
                System.out.println(ur);
                ur.assemble(uResource);
                //??????????????????????????????????????????????????????????????????????????????????????????????????????
                if (urdao.modifyResource(ur) > 0) message.setResultCode(ResultCode.SUCCESS);

                message.setResultCode(ResultCode.SUCCESS);
            }else message.setResultCode(ResultCode.ERROR_404);
        }
        return message;
    }

    /**
     * ?????????????????????????????????????????? ??????????????????
     * @param userResourceId ????????????id
     * @param model     ???????????? ???????????? 0 ??????????????? 1
     * @param userName
     * @return
     */
    @Transactional
    public boolean delUserResource(Integer userResourceId, int model, String userName) {
        Message message = new Message();
        User user = udao.inquireByName(userName);
        message.setResultCode(ResultCode.ERROR_500);
        boolean result = false;

        if (user != null) {
            UserResource ur =  urdao.inquireUserResourceById(userResourceId);
            ResourceRecycle rr = new ResourceRecycle();
            rr.setDeleteTime(new Date(System.currentTimeMillis()));
            rr.setOriginalName(ur.getOriginalName());
            rr.setResourceId(ur.getId());
            rr.setParentId(ur.getParentId());
            rr.setU_id(user.getId());
            rr.setTypeId(1);

            if (model == 0 && ur != null &&  !ur.getD_flag()) {
                ur.setD_flag(true);
                if (urdao.modifyResource(ur) > 0 && rrdao.addResourceRecycle(rr) > 0) {
                    usrdao.delelteShareResourceByURId(ur.getId());
                    result = true;
                }
            }else if(model == 1){
                if (rrdao.removeResourceRecycle(rr) > 0 && urdao.removeResource(ur) > 0) result = true;
            }

        }

        return result;
    }
    /**
     *
     * @param folderId  ?????????id
     * @param userName  ????????????
     * @param del       ??????????????????
     * @return
     */
    public Message delUserFolder ( int folderId, String userName,boolean del){
        Message message = new Message(ResultCode.ERROR_500);
        ResourceRecycle rr = new ResourceRecycle();
        User user = udao.inquireByName(userName);
        if(user != null){
            Folder folder = fdao.inquireFolderById(folderId);

            rr.setResourceId(folder.getFolderId());
            rr.setOriginalName(folder.getFolderName());
            rr.setParentId(folder.getParentId());
            rr.setU_id(user.getId());
            rr.setDeleteTime(new Date(System.currentTimeMillis()));
            rr.setTypeId(0);

            if(!del){
                setUserFolderStatus(folderId,user.getId(),del,true);

                delelteShareResource(folderId,user.getId());

                rrdao.addResourceRecycle(rr);
            }else{
                rrdao.removeResourceRecycle(rr);
            }

            message.setResultCode(ResultCode.SUCCESS);
        }

        return message;
    }

    /**
     *
     * @param folderId  ????????????????????????id
     * @param userId    ?????????
     * @param del       ????????????????????????????????????????????????
     * @param delFlag    ?????????????????????????????????????????????
     * @return
     */
    //????????????????????????
    @Transactional
    public boolean setUserFolderStatus ( int folderId, Integer userId,boolean del,boolean delFlag){
        Folder folder = new Folder();
        folder.setFolderId(folderId);
        folder.setD_flag(delFlag);

        boolean flag = false;

        //????????????
        if (!del ) {
            fdao.modifyFolder(folder);//????????????????????????????????????
            List<FolderDTO> folders = udao.inquireUserFolders(folderId, userId, !delFlag,null);
            List<ResourceDTO> resources = rdao.inquireUserResourceByParentId(folderId, userId, !delFlag,null);
            if (resources != null) {
                for (ResourceDTO re : resources) {
                    UserResource newRe = new UserResource();
                    newRe.setId(re.getId());
                    newRe.setFileName(re.getFileId());
                    newRe.setD_flag(delFlag);
                    newRe.setU_id(userId);

                    urdao.modifyResource(newRe);
                }
            }
            if (folders != null) {
                for (FolderDTO ele : folders) {
                    setUserFolderStatus(ele.getFolderId(),userId,del,delFlag);
                }
            }

            flag = true;
        }else if(del){
            //????????????
            folder.setUserId(userId);
            fdao.removeFolder(folder);
            List<FolderDTO> folders = udao.inquireUserFolders(folderId, userId, !delFlag,null);
            List<ResourceDTO> resources = rdao.inquireUserResourceByParentId(folderId, userId, !delFlag,null);
            if (resources != null) {
                for (ResourceDTO re : resources) {
                    UserResource newRe = new UserResource();
                    newRe.setResourceId(re.getId());
                    //newRe.setFileName(re.getFileId());
                    newRe.setU_id(userId);

                    urdao.removeResource(newRe);
                }
            }
            if (folders != null) {
                for (FolderDTO ele : folders) {
                    setUserFolderStatus(ele.getFolderId(),userId,del,delFlag);
                }
            }
            flag = true;
        }

        return flag;
    }

    public boolean getFolderIsEmpty(Integer folderId,String userName){
        User user = udao.inquireByName(userName);
        return FolderIsEmpty(folderId,user.getId());
    }

    private boolean FolderIsEmpty(Integer folderId,Integer userId) {
        boolean re = false;
        List<FolderDTO> folderDTOS = udao.inquireUserFolders(folderId, userId, false,null);
        List<UserResource> userResources = urdao.inquireUserResourceByParentId(folderId,userId,false);
        if(userResources.size() > 0)return re;
        else {
            for(FolderDTO f : folderDTOS){
                if(!FolderIsEmpty(f.getFolderId(), userId))return re;
            }
        }

        return true;
    }

    /**
     * @Description: ??????????????????????????????????????????
     * @author Turbine
     * @param
     * @param folderId
     * @return com.turbine.tnd.dto.RNavigationDTO
     * @date 2023/2/2 21:48
     */
    public RNavigationDTO getRLocation(Integer folderId) {
        Folder folder = fdao.inquireFolderById(folderId);
        RNavigationDTO re = null;
        if(folder != null ){
            re = new RNavigationDTO();
            re.setName(folder.getFolderName());
            re.setId(folderId);
            getAllParent(folder.getParentId(),re);
        }

        return re;
    }

    private void getAllParent(Integer folderId, RNavigationDTO re) {
        Folder f = fdao.inquireFolderById(folderId);
        if(f != null){
            RNavigationDTO n = new RNavigationDTO();
            n.setName(f.getFolderName());
            n.setId(f.getFolderId());
            re.setParent(n);

            getAllParent(f.getParentId(),n);
        }

    }

    /**
     * @Description: ???????????????????????????
     * @author Turbine
     * @param
     * @param folderId      ????????????id
     * @param userId        ????????????id
     * @param parentId
     * @return void
     * @date 2023/1/25 14:33
     */
    public void saveFolder(Integer folderId,Integer userId,Integer parentId) {

        Folder folder = fdao.inquireFolderById(folderId);
        int originalU_id = folder.getUserId();
        folder.setFolderId(null);
        folder.setUserId(userId);
        folder.setCreateTime(null);
        folder.setParentId(parentId);
        folder.setS_flag(false);

        if(fdao.addFolder(folder) > 0){
            List<UserResource> resources = urdao.inquireUserResourceByParentId(folderId,originalU_id,false);
            for(UserResource resource : resources){
                //??????????????????????????????????????????
                saveFile(resource,folder.getFolderId(),userId);
            }
            List<FolderDTO> folders = udao.inquireUserFolders(folderId, originalU_id, false,null);
            for(FolderDTO f : folders){
                saveFolder(f.getFolderId(),userId,folder.getFolderId());
            }
        }

    }


    public void saveFile(UserResource resource,Integer parentId,Integer userId) {
        resource.setU_id(userId);
        resource.setUploadTime(null);
        resource.setParentId(parentId);
        resource.setEncryption(false);
        resource.setEncryptPsw(null);
        resource.setS_flag(false);

        urdao.addUserResource(resource);
    }


    public void getResource(Integer resourceId, HttpServletResponse resp,Integer type,String userName) throws IOException {
        User user = udao.inquireByName(userName);
        getUResource(resourceId,type,resp,user.getId());
    }

   /* public void getResource(Integer resourceId, HttpServletResponse resp) throws IOException {
        UserResource userResource = urdao.inquireUserResourceById(resourceId);
        Resource resource = rdao.inquireById(userResource.getResourceId());


        File file = new File(resource.getLocation());
        byte[] body = null;
        InputStream is = new FileInputStream(file);
        body = new byte[1024];
        int read = 0;
        OutputStream  os = resp.getOutputStream();

        while((read = is.read(body) ) != -1){
            os.write(body,0,read);
        }
        is.read(body);

        HttpHeaders headers = new HttpHeaders();
        if("mp4".equals(resource.getType().getType())){
            resp.setContentType("video/mp4;charset=UTF-8");
        }else resp.addHeader("Content-Disposition", "attchement;filename=" +userResource.getOriginalName()+resource.getType().getType());

    }**/


    /**
     * @Description: ?????????????????????????????????????????????
     * @author Turbine
     * @param
     * @param zipOutput ???????????????
     * @param parentId  ?????????id
     * @param userId
     * @param dir       ??????????????????
     * @return void
     * @date 2023/2/4 15:55
     */
    public void buildCompressFolder(ZipOutputStream zipOutput, Integer parentId, Integer userId, String dir) throws IOException {
        List<FolderDTO> folders = udao.inquireUserFolders(parentId, userId, false,null);
        List<ResourceDTO> resource = rdao.inquireUserResourceByParentId(parentId, userId, false,null);

        ZipEntry entry = null;
        FolderDTO folderDTO = udao.inquireFolder(parentId);
        //?????????????????????
        if(dir.equals("")){
            dir = folderDTO.getFolderName();
        }else {
            dir = dir+File.separator+folderDTO.getFolderName();
        }

        if(resource != null){
            //zipOutput.putNextEntry(entry);
            List<File> files = new ArrayList<>();
            //int i=0;  (i++)+"_"
            for(ResourceDTO e : resource){

                UserResource ur = urdao.inquireUserResourceById(e.getId());
                Resource re = rdao.inquireById(ur.getResourceId());
                File file = new File(re.getLocation());


                //?????????????????????????????? ?????????????????? ZipEntry???????????????????????????
                zipOutput.putNextEntry(new ZipEntry(dir+File.separator+ur.getOriginalName()+re.getType().getType()));

                byte[] temp = new byte[1024];
                BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                int read = 0;

                while((read = bis.read(temp) )!= -1){
                    zipOutput.write(temp,0,read);
                }
                zipOutput.flush();
                bis.close();
                zipOutput.finish();//?????????????????????????????????????????????????????????????????????
                zipOutput.closeEntry();

            }

        }


        if(folders != null){
            for(FolderDTO f : folders){
                buildCompressFolder(zipOutput,f.getFolderId(),userId,dir);
            }
        }

    }


    /**
     * @Description: ???????????????????????????????????????
     * @author Turbine
     * @param
     * @param id        ???????????????id
     * @param userId
     * @return void
     * @date 2023/2/15 23:43
     */
    private void delelteShareResource(Integer id,Integer userId){
        List<UserResource> userResources = urdao.inquireUserResourceByParentId(id, userId, true);
        usrdao.delelteShareResourceByURId(id);
        if(userResources != null){
            for(UserResource ur : userResources){
                usrdao.delelteShareResourceByURId(ur.getId());
                delelteShareResource(ur.getId(),userId);
            }
        }
    }


    /**
     * @Description: ????????????????????????
     * @author Turbine
     * @param
     * @param userResourceId
     * @param resp
     * @return java.lang.String
     * @date 2023/2/19 17:26
     */

    public String getMovieLocation(Integer userResourceId) {
        String re = null;
        UserResource userResource = urdao.inquireUserResourceById(userResourceId);
        if(userResource != null){
            Resource resource = rdao.inquireByName(userResource.getFileName());
            String location = resource.getLocation();

            int first = location.lastIndexOf("/static");
            int end = location.lastIndexOf(".");
            re = location.substring(first+7, end) + ".m3u8";

        }
        return re;
    }
}

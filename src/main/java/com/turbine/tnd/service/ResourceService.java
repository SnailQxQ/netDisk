package com.turbine.tnd.service;

import com.turbine.tnd.bean.Folder;
import com.turbine.tnd.bean.UserResource;
import com.turbine.tnd.dao.*;
import com.turbine.tnd.dto.FolderDTO;
import com.turbine.tnd.dto.ResourceDTO;
import com.turbine.tnd.utils.FilterFactor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Turbine
 * @Description:
 * @date 2023/2/16 16:45
 */
@Service
@Slf4j
@SuppressWarnings("all")
public class ResourceService {

    @Autowired
    UserDao udao;
    @Autowired
    ResourceDao rdao;
    @Autowired
    FilterFactor filterFactor;
    @Autowired
    FolderDao fdao;
    @Autowired
    UserResourceDao urdao;



    /**
     *
     * @param folderId  要进行操作的文件id
     * @param userId    操作者
     * @param del       删除标识，是逻辑修改还是物理删除
     * @param delFlag    删除标记，仅在逻辑修改状态生效
     * @return
     */
    //递归更新删除标记
    @Transactional
    public boolean setUserFolderStatus ( int folderId, Integer userId,boolean del,boolean delFlag){
        Folder folder = new Folder();
        folder.setFolderId(folderId);
        folder.setD_flag(delFlag);

        boolean flag = false;

        //逻辑删除
        if (!del ) {
            fdao.modifyFolder(folder);//设置文件夹自己为删除状态
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
            //物理删除
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

}

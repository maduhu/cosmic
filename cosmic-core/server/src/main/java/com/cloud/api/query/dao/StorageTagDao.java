package com.cloud.api.query.dao;

import com.cloud.api.query.vo.StorageTagVO;
import com.cloud.api.response.StorageTagResponse;
import com.cloud.utils.db.GenericDao;

import java.util.List;

public interface StorageTagDao extends GenericDao<StorageTagVO, Long> {
    StorageTagResponse newStorageTagResponse(StorageTagVO storageTag);

    List<StorageTagVO> searchByIds(Long... storageTagIds);
}

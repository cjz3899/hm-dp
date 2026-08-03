package com.junzhecai.hmdp.controller.Support;

import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.User;
import com.junzhecai.hmdp.model.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class UserVoAssembler {
    public abstract UserVO toUserVO(UserDTO userDTO);

    public abstract UserVO toUserVO(User user);

    public abstract List<UserVO> toUserVOList(List<User> users);
}

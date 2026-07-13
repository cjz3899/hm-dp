package com.junzhecai.hmdp.service.Impl.support;

import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;


@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public class UserAssembler {
    public static UserDTO toUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setPhone(user.getPhone());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        return userDTO;
    }
}

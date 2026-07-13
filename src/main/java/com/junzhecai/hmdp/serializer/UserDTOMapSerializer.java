package com.junzhecai.hmdp.serializer;

import com.junzhecai.hmdp.model.dto.UserDTO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class UserDTOMapSerializer {
    public Map<String, String> serialize(UserDTO userDTO) {
        Map<String, String> map = new HashMap<>();
        if (userDTO.getId() != null) map.put("id", userDTO.getId().toString());
        if (userDTO.getPhone() != null) map.put("phone", userDTO.getPhone());
        if (userDTO.getNickName() != null) map.put("nickName", userDTO.getNickName());
        if (userDTO.getIcon() != null) map.put("icon", userDTO.getIcon());
        return map;
    }
}

package ru.fisher.ToolsMarket.dto.UserDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFilterDto {
    private Long id;
    private String displayName;  // username или firstName + lastName
}

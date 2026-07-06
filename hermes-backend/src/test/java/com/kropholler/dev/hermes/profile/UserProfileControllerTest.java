package com.kropholler.dev.hermes.profile;

import com.kropholler.dev.hermes.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import(SecurityConfig.class)
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserProfileService userProfileService;
    @MockitoBean UserProfileApiMapper userProfileApiMapper;

    @Test
    void getProfile_usesSubjectFromJwtNotFromRequest() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = AddressDto.empty();
        when(userProfileService.getProfile(subject)).thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(get("/api/profile")
                .with(jwt().jwt(builder -> builder.subject(subject.toString()))))
            .andExpect(status().isOk());

        verify(userProfileService).getProfile(eq(subject));
    }

    @Test
    void updateAddress_usesSubjectFromJwtNotFromRequest() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = new AddressDto("Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht", 52.09, 5.12);
        when(userProfileService.updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht")))
            .thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString())))
                .contentType("application/json")
                .content("""
                    {"street":"Dorpstraat","houseNumber":"10","zipCode":"1234AB","city":"Utrecht","province":"Utrecht"}
                    """))
            .andExpect(status().isOk());

        verify(userProfileService).updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht"));
    }

    @Test
    void updateAddress_syncsEmailFromJwt() throws Exception {
        UUID subject = UUID.randomUUID();
        AddressDto dto = new AddressDto("Dorpstraat", "10", null, "1234AB", "Utrecht", "Utrecht", 52.09, 5.12);
        when(userProfileService.updateAddress(eq(subject), eq("Dorpstraat"), eq("10"), eq(null), eq("1234AB"), eq("Utrecht"), eq("Utrecht")))
            .thenReturn(dto);
        when(userProfileApiMapper.toResponse(dto)).thenReturn(new com.kropholler.dev.hermes.profile.openapi.AddressResponse());

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString()).claim("email", "user@hermes.local")))
                .contentType("application/json")
                .content("""
                    {"street":"Dorpstraat","houseNumber":"10","zipCode":"1234AB","city":"Utrecht","province":"Utrecht"}
                    """))
            .andExpect(status().isOk());

        verify(userProfileService).syncEmail(subject, "user@hermes.local");
    }

    @Test
    void updateAddress_returns422WhenGeocodingFails() throws Exception {
        UUID subject = UUID.randomUUID();
        when(userProfileService.updateAddress(eq(subject), eq("Nonexistent"), eq("999"), eq(null), eq(null), eq("Nowhereville"), eq(null)))
            .thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Address could not be geocoded"));

        mockMvc.perform(put("/api/profile/address")
                .with(jwt().jwt(builder -> builder.subject(subject.toString())))
                .contentType("application/json")
                .content("""
                    {"street":"Nonexistent","houseNumber":"999","city":"Nowhereville"}
                    """))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.detail").value("Address could not be geocoded"));
    }
}

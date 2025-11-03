package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Video Upload", description = "Receive short selfie videos required for liveness validation.")
public class VideoController {

    private final VideoService videoService;

    @Operation(
            summary = "Upload a verification video",
            description = "Accepts a selfie video up to 10 MB, generates an inquiry token, stores the payload, and "
                    + "triggers the VIDEO_UPLOADED workflow event."
    )
    @PostMapping(path = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestPart("video") MultipartFile video,
            @RequestPart("processInstanceId") String processInstanceId) {
        return videoService.uploadVideo(video, processInstanceId);
    }
}

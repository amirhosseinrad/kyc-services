package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.service.VideoService;
import ir.ipaam.kycservices.application.service.dto.VideoUploadRequest;
import ir.ipaam.kycservices.application.service.dto.VideoUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Biometric Service", description = "Receive short selfie videos required for liveness validation.")
public class VideoController {

    private final VideoService videoService;

    @Operation(
            summary = "Upload a verification video",
            description = "Accepts a selfie video up to 10 MB, generates an inquiry token, stores the payload, and "
                    + "triggers the VIDEO_UPLOADED workflow event."
    )
    @PostMapping(path = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VideoUploadResponse> uploadVideo(
            @RequestPart("video") MultipartFile video,
            @RequestPart("image1") MultipartFile image,
            @RequestPart("processInstanceId") String processInstanceId) {
        VideoUploadRequest request = new VideoUploadRequest(video, image, processInstanceId);
        VideoUploadResponse response = videoService.uploadVideo(request);
        HttpStatus status = resolveStatus(response);
        return ResponseEntity.status(status).body(response);
    }

    private HttpStatus resolveStatus(VideoUploadResponse response) {
        if ("VIDEO_ALREADY_UPLOADED".equals(response.status())) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.ACCEPTED;
    }
}

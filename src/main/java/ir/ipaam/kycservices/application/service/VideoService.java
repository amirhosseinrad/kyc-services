package ir.ipaam.kycservices.application.service;

import ir.ipaam.kycservices.application.service.dto.VideoUploadRequest;
import ir.ipaam.kycservices.application.service.dto.VideoUploadResponse;

public interface VideoService {

    VideoUploadResponse uploadVideo(VideoUploadRequest request);
}

package com.community.service;

import com.community.dto.FurnitureDto;
import com.community.dto.RoomDto;
import com.community.model.PersonalRoom;
import com.community.model.RoomFurniture;
import com.community.repository.PersonalRoomRepository;
import com.community.repository.RoomFurnitureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 개인 룸 관리 서비스
 * 데이터베이스에 영구 저장하고 메모리 캐시도 유지합니다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PersonalRoomService {

    private final PersonalRoomRepository personalRoomRepository;
    private final RoomFurnitureRepository roomFurnitureRepository;
    private final ActiveUserService activeUserService;

    // 메모리 캐시: roomId -> RoomDto 매핑 (빠른 조회용)
    private final Map<String, RoomDto> activeRoomsCache = new ConcurrentHashMap<>();
    
    // hostId -> roomId 매핑 (호스트가 만든 방 추적)
    private final Map<String, String> hostToRoom = new ConcurrentHashMap<>();

    /**
     * 서비스 시작 시 DB에서 활성 방 로드
     */
    @Transactional(readOnly = true)
    public void loadActiveRoomsFromDB() {
        List<PersonalRoom> activeRooms = personalRoomRepository.findByIsActiveTrue();
        for (PersonalRoom room : activeRooms) {
            RoomDto dto = entityToDto(room);
            activeRoomsCache.put(room.getRoomId(), dto);
            hostToRoom.put(String.valueOf(room.getHostId()), room.getRoomId());
        }
        log.info("Loaded {} active rooms from database", activeRooms.size());
    }

    /**
     * 방 생성 또는 기존 방 반환
     */
    @Transactional
    public RoomDto createRoom(RoomDto roomDto) {
        if (roomDto == null || roomDto.getRoomId() == null) {
            log.warn("Invalid room data for creation");
            return null;
        }
        
        String hostId = roomDto.getHostId();
        
        // 호스트가 이미 방을 가지고 있는지 DB에서 확인
        if (hostId != null) {
            Long hostIdLong = Long.parseLong(hostId);
            Optional<PersonalRoom> existingRoom = personalRoomRepository.findByHostIdAndIsActiveTrue(hostIdLong);
            
            if (existingRoom.isPresent()) {
                // 이미 방이 있으면 기존 방 정보 반환
                PersonalRoom room = existingRoom.get();
                log.info("Host {} already has a room: {}. Returning existing room.", hostId, room.getRoomId());
                
                RoomDto dto = entityToDto(room);
                dto.setAction("existing");
                
                // 캐시 업데이트
                activeRoomsCache.put(room.getRoomId(), dto);
                hostToRoom.put(hostId, room.getRoomId());
                
                return dto;
            }
        }
        
        // 새 방 생성
        PersonalRoom newRoom = PersonalRoom.builder()
                .roomId(roomDto.getRoomId())
                .roomName(roomDto.getRoomName())
                .hostId(hostId != null ? Long.parseLong(hostId) : null)
                .hostName(roomDto.getHostName())
                .maxMembers(roomDto.getMaxMembers() != null ? roomDto.getMaxMembers() : 10)
                .isPrivate(roomDto.getIsPrivate() != null ? roomDto.getIsPrivate() : true)
                .gpsLng(roomDto.getGpsLng())
                .gpsLat(roomDto.getGpsLat())
                .isActive(true)
                .build();
        
        PersonalRoom saved = personalRoomRepository.save(newRoom);
        log.info("Room created in DB: roomId={}, roomName={}, hostId={}", 
                saved.getRoomId(), saved.getRoomName(), hostId);
        
        // DTO 변환 및 캐시 저장
        RoomDto dto = entityToDto(saved);
        dto.setAction("create");
        dto.setTimestamp(System.currentTimeMillis());
        
        activeRoomsCache.put(saved.getRoomId(), dto);
        if (hostId != null) {
            hostToRoom.put(hostId, saved.getRoomId());
        }
        
        return dto;
    }

    /**
     * 방 삭제 (호스트만 삭제 가능)
     */
    @Transactional
    public RoomDto deleteRoom(String roomId) {
        if (roomId == null) {
            return null;
        }
        
        Optional<PersonalRoom> roomOpt = personalRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            // 캐시에서만 제거
            RoomDto cached = activeRoomsCache.remove(roomId);
            if (cached != null && cached.getHostId() != null) {
                hostToRoom.remove(cached.getHostId());
            }
            return cached;
        }
        
        PersonalRoom room = roomOpt.get();
        String hostId = String.valueOf(room.getHostId());
        
        // DB에서 삭제
        personalRoomRepository.delete(room);
        log.info("Room deleted from DB: roomId={}", roomId);
        
        // 캐시에서 제거
        activeRoomsCache.remove(roomId);
        hostToRoom.remove(hostId);
        
        RoomDto dto = entityToDto(room);
        dto.setAction("delete");
        dto.setTimestamp(System.currentTimeMillis());
        
        return dto;
    }

    /**
     * 방 조회
     */
    @Transactional(readOnly = true)
    public RoomDto getRoom(String roomId) {
        // 캐시 먼저 확인
        RoomDto cached = activeRoomsCache.get(roomId);
        if (cached != null) {
            return cached;
        }
        
        // DB에서 조회
        Optional<PersonalRoom> roomOpt = personalRoomRepository.findByRoomId(roomId);
        if (roomOpt.isPresent()) {
            RoomDto dto = entityToDto(roomOpt.get());
            activeRoomsCache.put(roomId, dto);
            return dto;
        }
        
        return null;
    }

    /**
     * 모든 활성 방 목록 조회 (호스트가 온라인인 방만)
     */
    @Transactional(readOnly = true)
    public List<RoomDto> getAllRooms() {
        // DB에서 최신 데이터 로드
        List<PersonalRoom> rooms = personalRoomRepository.findByIsActiveTrue();
        return rooms.stream()
                .filter(room -> {
                    // 호스트가 온라인인 방만 필터링
                    if (room.getHostId() != null) {
                        String hostId = String.valueOf(room.getHostId());
                        boolean isOnline = activeUserService.isUserActive(hostId);
                        if (!isOnline) {
                            log.debug("방 {} 필터링됨: 호스트 {}가 오프라인", room.getRoomId(), hostId);
                        }
                        return isOnline;
                    }
                    // 호스트 ID가 없는 경우 표시 (안전장치)
                    return true;
                })
                .map(this::entityToDto)
                .collect(Collectors.toList());
    }

    /**
     * 호스트 ID로 방 조회
     */
    @Transactional(readOnly = true)
    public RoomDto getRoomByHostId(String hostId) {
        if (hostId == null) return null;
        
        try {
            Long hostIdLong = Long.parseLong(hostId);
            Optional<PersonalRoom> roomOpt = personalRoomRepository.findByHostIdAndIsActiveTrue(hostIdLong);
            return roomOpt.map(this::entityToDto).orElse(null);
        } catch (NumberFormatException e) {
            log.warn("Invalid hostId format: {}", hostId);
            return null;
        }
    }

    /**
     * 호스트가 이미 방을 가지고 있는지 확인
     */
    @Transactional(readOnly = true)
    public boolean hasRoom(String hostId) {
        if (hostId == null) return false;
        
        try {
            Long hostIdLong = Long.parseLong(hostId);
            return personalRoomRepository.findByHostIdAndIsActiveTrue(hostIdLong).isPresent();
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 호스트가 나갈 때 방 삭제 (연결 해제 시 호출 - 더 이상 자동 삭제하지 않음)
     * 이제 명시적인 삭제 요청이 있을 때만 삭제
     */
    @Transactional
    public RoomDto deleteRoomByHostId(String hostId) {
        String roomId = hostToRoom.get(hostId);
        if (roomId != null) {
            return deleteRoom(roomId);
        }
        return null;
    }

    /**
     * 활성 방 개수
     */
    public int getRoomCount() {
        return activeRoomsCache.size();
    }

    /**
     * 특정 위치 주변의 방 목록 조회 (GPS 기반, 호스트가 온라인인 방만)
     */
    @Transactional(readOnly = true)
    public List<RoomDto> getNearbyRooms(double lng, double lat, double radiusKm) {
        List<PersonalRoom> allRooms = personalRoomRepository.findByIsActiveTrue();
        List<RoomDto> nearbyRooms = new ArrayList<>();
        
        for (PersonalRoom room : allRooms) {
            // 호스트가 온라인인지 확인
            if (room.getHostId() != null) {
                String hostId = String.valueOf(room.getHostId());
                if (!activeUserService.isUserActive(hostId)) {
                    log.debug("주변 방 {} 필터링됨: 호스트 {}가 오프라인", room.getRoomId(), hostId);
                    continue;
                }
            }
            
            if (room.getGpsLng() != null && room.getGpsLat() != null) {
                double distance = calculateDistance(lat, lng, room.getGpsLat(), room.getGpsLng());
                if (distance <= radiusKm) {
                    nearbyRooms.add(entityToDto(room));
                }
            } else {
                // GPS 정보가 없는 방은 모두 포함 (전역 방)
                nearbyRooms.add(entityToDto(room));
            }
        }
        
        return nearbyRooms;
    }

    // ==================== 가구 관련 메서드 ====================

    /**
     * 방의 모든 가구 조회
     */
    @Transactional(readOnly = true)
    public List<FurnitureDto> getFurnitures(String roomId) {
        Optional<PersonalRoom> roomOpt = personalRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<RoomFurniture> furnitures = roomFurnitureRepository.findByPersonalRoomIdAndIsVisibleTrue(roomOpt.get().getId());
        return furnitures.stream()
                .map(this::furnitureEntityToDto)
                .collect(Collectors.toList());
    }

    /**
     * 가구 추가 또는 업데이트
     */
    @Transactional
    public FurnitureDto saveFurniture(String roomId, FurnitureDto furnitureDto) {
        Optional<PersonalRoom> roomOpt = personalRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            log.warn("Room not found for furniture save: {}", roomId);
            return null;
        }
        
        PersonalRoom room = roomOpt.get();
        
        // 기존 가구 찾기
        Optional<RoomFurniture> existingOpt = roomFurnitureRepository
                .findByPersonalRoomIdAndFurnitureId(room.getId(), furnitureDto.getFurnitureId());
        
        RoomFurniture furniture;
        if (existingOpt.isPresent()) {
            // 업데이트
            furniture = existingOpt.get();
            updateFurnitureFromDto(furniture, furnitureDto);
        } else {
            // 새로 생성
            furniture = RoomFurniture.builder()
                    .personalRoom(room)
                    .furnitureId(furnitureDto.getFurnitureId())
                    .furnitureType(furnitureDto.getFurnitureType())
                    .modelPath(furnitureDto.getModelPath())
                    .posX(furnitureDto.getPosX() != null ? furnitureDto.getPosX() : 0.0)
                    .posY(furnitureDto.getPosY() != null ? furnitureDto.getPosY() : 0.0)
                    .posZ(furnitureDto.getPosZ() != null ? furnitureDto.getPosZ() : 0.0)
                    .rotX(furnitureDto.getRotX() != null ? furnitureDto.getRotX() : 0.0)
                    .rotY(furnitureDto.getRotY() != null ? furnitureDto.getRotY() : 0.0)
                    .rotZ(furnitureDto.getRotZ() != null ? furnitureDto.getRotZ() : 0.0)
                    .scaleX(furnitureDto.getScaleX() != null ? furnitureDto.getScaleX() : 1.0)
                    .scaleY(furnitureDto.getScaleY() != null ? furnitureDto.getScaleY() : 1.0)
                    .scaleZ(furnitureDto.getScaleZ() != null ? furnitureDto.getScaleZ() : 1.0)
                    .isVisible(true)
                    .color(furnitureDto.getColor())
                    .customData(furnitureDto.getCustomData())
                    .build();
        }
        
        RoomFurniture saved = roomFurnitureRepository.save(furniture);
        log.info("Furniture saved: roomId={}, furnitureId={}", roomId, furnitureDto.getFurnitureId());
        
        return furnitureEntityToDto(saved);
    }

    /**
     * 여러 가구 일괄 저장
     */
    @Transactional
    public List<FurnitureDto> saveFurnitures(String roomId, List<FurnitureDto> furnitureDtos) {
        List<FurnitureDto> savedList = new ArrayList<>();
        for (FurnitureDto dto : furnitureDtos) {
            FurnitureDto saved = saveFurniture(roomId, dto);
            if (saved != null) {
                savedList.add(saved);
            }
        }
        return savedList;
    }

    /**
     * 가구 삭제 (isVisible = false로 설정)
     */
    @Transactional
    public boolean deleteFurniture(String roomId, String furnitureId) {
        Optional<PersonalRoom> roomOpt = personalRoomRepository.findByRoomId(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }
        
        Optional<RoomFurniture> furnitureOpt = roomFurnitureRepository
                .findByPersonalRoomIdAndFurnitureId(roomOpt.get().getId(), furnitureId);
        
        if (furnitureOpt.isPresent()) {
            RoomFurniture furniture = furnitureOpt.get();
            furniture.setIsVisible(false);
            roomFurnitureRepository.save(furniture);
            log.info("Furniture hidden: roomId={}, furnitureId={}", roomId, furnitureId);
            return true;
        }
        
        return false;
    }

    // ==================== Helper 메서드 ====================

    private RoomDto entityToDto(PersonalRoom entity) {
        RoomDto dto = new RoomDto();
        dto.setRoomId(entity.getRoomId());
        dto.setRoomName(entity.getRoomName());
        dto.setHostId(entity.getHostId() != null ? String.valueOf(entity.getHostId()) : null);
        dto.setHostName(entity.getHostName());
        dto.setMaxMembers(entity.getMaxMembers());
        dto.setIsPrivate(entity.getIsPrivate());
        dto.setGpsLng(entity.getGpsLng());
        dto.setGpsLat(entity.getGpsLat());
        dto.setGameName("개인 룸");
        dto.setMembers(1);
        return dto;
    }

    private FurnitureDto furnitureEntityToDto(RoomFurniture entity) {
        return FurnitureDto.builder()
                .id(entity.getId())
                .furnitureId(entity.getFurnitureId())
                .furnitureType(entity.getFurnitureType())
                .modelPath(entity.getModelPath())
                .posX(entity.getPosX())
                .posY(entity.getPosY())
                .posZ(entity.getPosZ())
                .rotX(entity.getRotX())
                .rotY(entity.getRotY())
                .rotZ(entity.getRotZ())
                .scaleX(entity.getScaleX())
                .scaleY(entity.getScaleY())
                .scaleZ(entity.getScaleZ())
                .isVisible(entity.getIsVisible())
                .color(entity.getColor())
                .customData(entity.getCustomData())
                .build();
    }

    private void updateFurnitureFromDto(RoomFurniture entity, FurnitureDto dto) {
        if (dto.getPosX() != null) entity.setPosX(dto.getPosX());
        if (dto.getPosY() != null) entity.setPosY(dto.getPosY());
        if (dto.getPosZ() != null) entity.setPosZ(dto.getPosZ());
        if (dto.getRotX() != null) entity.setRotX(dto.getRotX());
        if (dto.getRotY() != null) entity.setRotY(dto.getRotY());
        if (dto.getRotZ() != null) entity.setRotZ(dto.getRotZ());
        if (dto.getScaleX() != null) entity.setScaleX(dto.getScaleX());
        if (dto.getScaleY() != null) entity.setScaleY(dto.getScaleY());
        if (dto.getScaleZ() != null) entity.setScaleZ(dto.getScaleZ());
        if (dto.getColor() != null) entity.setColor(dto.getColor());
        if (dto.getCustomData() != null) entity.setCustomData(dto.getCustomData());
        entity.setIsVisible(true);
    }

    /**
     * 두 GPS 좌표 사이의 거리 계산 (Haversine 공식)
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371; // 지구 반경 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c;
    }
}

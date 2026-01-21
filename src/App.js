import React, { Suspense, useRef, useState, useEffect, useCallback, useMemo } from 'react';
import { Canvas } from '@react-three/fiber';
import './App.css';
import { Physics } from '@react-three/rapier';
import mapboxgl from 'mapbox-gl';
import { Mapbox3D } from './features/map';
import { LandingPage } from './features/auth';
import { BoardModal } from './features/board';
import { ProfileModal } from './features/profile';
import { SettingModal } from './features/system/settings';
import { EventModal } from './features/event';
import { MinigameModal } from './features/minigame';
import Character from './components/character/Character';
import MapCharacterController from './components/character/MapCharacterController';
import CameraController from './components/camera/CameraController';
import CameraLogger from './components/camera/CameraLogger';
import Level1 from './components/map/Level1';
import MapFloor from './components/map/MapFloor';
import GlobalChat from './components/GlobalChat';
import OtherPlayer from './components/character/OtherPlayer';
import ProfileAvatar from './components/ProfileAvatar';
import PhoneUI from './components/PhoneUI';
import SuspensionNotification from './components/SuspensionNotification';
import ContextMenu from './components/ContextMenu';
import OtherPlayerProfileModal from './components/OtherPlayerProfileModal';
import Notification from './components/Notification';
import NotificationModal from './components/NotificationModal';
import NotificationToast from './components/NotificationToast';
import GameIcon from './components/GameIcon';
import CurrencyDisplay from './components/CurrencyDisplay';
import multiplayerService from './services/multiplayerService';
import minigameService from './services/minigameService';
import notificationService from './services/notificationService';
import authService from './features/auth/services/authService';
import friendService from './services/friendService';
import currencyService from './services/currencyService';
import attendanceService from './services/attendanceService';
import shopService from './features/shop/services/shopService';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ShopModal } from './features/shop';
import { GoldChargeModal } from './features/payment';
import { InventoryModal } from './features/inventory';


function App() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const characterRef = useRef();
  const mainCameraRef = useRef();
  const level1PositionRef = useRef(null); // Level1 위치를 ref로 저장 (즉시 접근 용도)
  const mapReadyCalledRef = useRef(false); // handleMapReady가 한 번만 호출되도록 제어
  const lastMapUpdateRef = useRef(0); // 지도 업데이트 throttle
  const initialMapCenterRef = useRef(null); // 초기 지도 중심 좌표 저장
  const lastCharacterPositionRef = useRef([0, 0, 0]); // 지난 캐릭터 위치 저장
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [showLanding, setShowLanding] = useState(true);
  const [mapHelpers, setMapHelpers] = useState(null);
  const [initialPosition, setInitialPosition] = useState(null);
  const [level1Position, setLevel1Position] = useState(null); // Level1 위치 저장 (state)
  const [isMapFull, setIsMapFull] = useState(false);
  const [showBoardModal, setShowBoardModal] = useState(false);
  const [showProfileModal, setShowProfileModal] = useState(false);
  const [showSettingModal, setShowSettingModal] = useState(false);
  const [showEventModal, setShowEventModal] = useState(false);
  const [showMinigameModal, setShowMinigameModal] = useState(false);
  const [minigameModalMode, setMinigameModalMode] = useState('lobby'); // 'lobby' or 'create'
  const [pendingJoinRoomId, setPendingJoinRoomId] = useState(null);
  const [showShopModal, setShowShopModal] = useState(false);
  const [showInventoryModal, setShowInventoryModal] = useState(false);
  const [showGoldChargeModal, setShowGoldChargeModal] = useState(false);
  const [goldChargeModalTab, setGoldChargeModalTab] = useState('charge'); // 'charge' | 'exchange'
  const [shouldAutoAttendance, setShouldAutoAttendance] = useState(false);
  const [showPhoneUI, setShowPhoneUI] = useState(false);
  const [phoneInitialFriend, setPhoneInitialFriend] = useState(null); // DM 알림 클릭 시 열 친구 정보
  const [username, setUsername] = useState('');
  const [userId, setUserId] = useState('');
  const [isMenuExpanded, setIsMenuExpanded] = useState(false);
  const [otherPlayers, setOtherPlayers] = useState({});
  const [userProfile, setUserProfile] = useState(null); // 사용자 프로필 (selectedProfile, selectedOutline 포함)
  const [onlineCount, setOnlineCount] = useState(0); // 온라인 인원 수
  const [playerJoinEvent, setPlayerJoinEvent] = useState(null); // 플레이어 입장 이벤트
  const [playerLeaveEvent, setPlayerLeaveEvent] = useState(null); // 플레이어 퇴장 이벤트
  const [isChatInputFocused, setIsChatInputFocused] = useState(false); // 채팅 입력 포커스 상태
  const [playerChatMessages, setPlayerChatMessages] = useState({}); // 플레이어별 채팅 메시지 { userId: { message, timestamp } }
  const [myChatMessage, setMyChatMessage] = useState(''); // 내 캐릭터의 채팅 메시지
  const myMessageTimerRef = useRef(null); // 내 메시지 타이머 참조
  const playerMessageTimersRef = useRef({}); // 다른 플레이어 메시지 타이머 참조
  const [contextMenu, setContextMenu] = useState(null); // 컨텍스트 메뉴 상태 { position: {x, y}, playerData: {userId, username} }
  const [otherPlayerProfile, setOtherPlayerProfile] = useState(null); // 다른 플레이어 프로필 모달 상태 { userId, username }
  const [notification, setNotification] = useState(null); // 알림 상태 { message, type }
  const [showGameIcon, setShowGameIcon] = useState(false); // 게임 아이콘 표시 상태
  const [silverCoins, setSilverCoins] = useState(0); // 일반 재화 (Silver Coin)
  const [goldCoins, setGoldCoins] = useState(0); // 유료 재화 (Gold Coin)
  const [showNotificationModal, setShowNotificationModal] = useState(false); // 알림 모달 표시 상태
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0); // 읽지 않은 알림 개수
  const [toastNotifications, setToastNotifications] = useState([]); // 실시간 토스트 알림 목록
  const [characterModelPath, setCharacterModelPathState] = useState('/resources/Ultimate Animated Character Pack - Nov 2019/glTF/BaseCharacter.gltf');
  const [isChangingModel, setIsChangingModel] = useState(false);
  const [appSettings, setAppSettings] = useState(() => {
    // 로컬 스토리지에서 설정 불러오기
    try {
      const stored = localStorage.getItem('appSettings');
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (error) {
      console.error('Failed to load app settings:', error);
    }
    return {
      graphics: {
        quality: 'basic',
        shadows: 'on'
      },
      sound: {
        master: 70,
        effects: 80,
        music: 60
      },
      other: {
        showToastNotifications: true,
        chatNotifications: true,
        eventNotifications: true,
        nightNotifications: false
      }
    };
  });
  const mapboxToken = process.env.REACT_APP_MAPBOX_TOKEN || 'pk.eyJ1IjoiYmluc3MwMTI0IiwiYSI6ImNtaTcyM24wdjAwZDMybHEwbzEyenJ2MjEifQ.yi82NwUcsPMGP4M3Ri136g';

  // 모달이 열려있는지 확인 (PhoneUI는 제외 - 게임플레이에 영향 없음)
  const isAnyModalOpen = showBoardModal || showProfileModal || showSettingModal || showEventModal || showMinigameModal || showShopModal || showInventoryModal || showGoldChargeModal || showLanding || showNotificationModal;

  // 캐릭터 현재 위치 업데이트 콜백
  const handleCharacterPositionUpdate = useCallback((position) => {
    if (!isMapFull) {
      // Level1 모드일 때만 위치 저장
      level1PositionRef.current = position;
      setLevel1Position(position);
    }
  }, [isMapFull]);

  // 지도 모드에서 캐릭터 위치 업데이트 - Mapbox 지도 중심 이동 (y축 고정, throttle 적용)
  const handleMapCharacterPositionUpdate = useCallback((position) => {
    if (!mapHelpers || !mapHelpers.map || !mapHelpers.project) return;
    if (!initialMapCenterRef.current) return;

    // 100ms마다 지도 업데이트 (테스트용)
    const now = Date.now();
    if (now - lastMapUpdateRef.current < 100) {
      return;
    }
    lastMapUpdateRef.current = now;

    const [threeX, threeY, threeZ] = position;

    try {
      const map = mapHelpers.map;
      const project = mapHelpers.project;

      // 초기 지도 중심을 Mercator로 변환
      const initialCenter = initialMapCenterRef.current;
      const initialMerc = project([initialCenter.lng, initialCenter.lat], 0);
      const unitsPerMeter = initialMerc.meterInMercatorCoordinateUnits || 1;

      // console.log('🗺️ [1] initialCenter:', initialCenter);
      // console.log('🗺️ [2] initialMerc.translateX/Y:', initialMerc.translateX, initialMerc.translateY);
      // console.log('🗺️ [3] unitsPerMeter:', unitsPerMeter);
      // console.log('🗺️ [4] characterPos(Three.js):', threeX, threeZ);

      // Three.js 좌표를 Mercator 단위로 변환 (x, z만 변환 - y축 무시)
      const dxMeters = threeX;
      const dzMeters = -threeZ; // Z는 반대 방향
      const dxMerc = dxMeters * unitsPerMeter;
      const dzMerc = dzMeters * unitsPerMeter;

      // console.log('🗺️ [5] dxMerc/dzMerc:', dxMerc, dzMerc);

      // 새로운 Mercator 좌표 = 초기 위치 + 이동량
      const newMercX = initialMerc.translateX + dxMerc;
      const newMercY = initialMerc.translateY + dzMerc;

      // console.log('🗺️ [6] newMercX/Y:', newMercX, newMercY);

      // Mercator 좌표를 LngLat으로 변환
      const mercatorCoord = new mapboxgl.MercatorCoordinate(newMercX, newMercY, 0);
      const lngLat = mercatorCoord.toLngLat();

      // console.log('🗺️ [7] converted to lngLat:', lngLat);

      // 지도 중심 업데이트
      map.setCenter(lngLat);
      // console.log('✅ [8] Map.setCenter() called with:', lngLat);
    } catch (e) {
      console.warn('❌ Map position update failed:', e);
    }
  }, [mapHelpers]);

  // 캐릭터 이동을 막아야 하는 상태 (모달 열림 또는 채팅 입력 중)
  const shouldBlockMovement = isAnyModalOpen || isChatInputFocused;

  // Map가 준비되면 호출됩니다. mapbox의 projection helper를 받아와
  // 현재 위치(geolocation)를 Three.js 월드 좌표로 변환해 캐릭터 초기 위치를 설정합니다.
  const handleMapReady = ({ map, project }) => {
    // handleMapReady가 여러 번 호출되지 않도록 제어
    if (mapReadyCalledRef.current) {
      console.warn('⚠️  handleMapReady already called, skipping');
      return;
    }
    mapReadyCalledRef.current = true;

    setMapHelpers({ map, project });

    // 초기 지도 중심 저장 (지도 업데이트용)
    const initialCenter = map.getCenter();
    initialMapCenterRef.current = initialCenter;
    console.log('🗺️ Initial map center saved:', initialCenter);

    // Try to get browser geolocation; fallback to map center
    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lng = pos.coords.longitude;
          const lat = pos.coords.latitude;

          try {
            const center = map.getCenter();
            const centerMerc = project([center.lng, center.lat], 0);
            const userMerc = project([lng, lat], 0);

            // Convert mercator units difference to meters
            const unitsPerMeter = userMerc.meterInMercatorCoordinateUnits || 1;
            const dx = (userMerc.translateX - centerMerc.translateX) / unitsPerMeter;
            const dz = (userMerc.translateY - centerMerc.translateY) / unitsPerMeter;

            // Mapbox의 Y increases northwards; Three.js Z forward is negative, adjust sign if needed
            const threeX = dx;
            const threeY = 0; // 지도 지면과 동일한 높이
            const threeZ = -dz;

            setInitialPosition([threeX, threeY, threeZ]);
            console.log('✅ Initial character position (Three.js):', [threeX, threeY, threeZ]);
          } catch (e) {
            console.warn('map projection failed', e);
          }
        },
        (err) => {
          console.warn('Geolocation denied or unavailable, using map center', err);
          // use map center as fallback
          const threeX = 0;
          const threeY = 0; // 지도 지면과 동일한 높이
          const threeZ = 0;
          setInitialPosition([threeX, threeY, threeZ]);
        }
      );
    } else {
      console.warn('Geolocation not supported, using map center');
      setInitialPosition([0, 0, 0]); // 지도 지면과 동일한 높이
    }
  };

  const toggleMapFull = (e) => {
    e && e.stopPropagation();
    navigate('/map-game');
  };

  // Helper: request geolocation and set initialPosition using provided project helper
  const requestGeolocationAndSet = (project, map) => {
    if (!project || !map) return;

    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const lng = pos.coords.longitude;
          const lat = pos.coords.latitude;
          try {
            const center = map.getCenter();
            const centerMerc = project([center.lng, center.lat], 0);
            const userMerc = project([lng, lat], 0);
            const unitsPerMeter = userMerc.meterInMercatorCoordinateUnits || 1;
            const dx = (userMerc.translateX - centerMerc.translateX) / unitsPerMeter;
            const dz = (userMerc.translateY - centerMerc.translateY) / unitsPerMeter;
            const threeX = dx;
            const threeY = 2;
            const threeZ = -dz;
            setInitialPosition([threeX, threeY, threeZ]);
            console.log('Initial character position (from toggle):', [threeX, threeY, threeZ]);
          } catch (e) {
            console.warn('map projection failed', e);
          }
        },
        (err) => {
          console.warn('Geolocation denied/unavailable when opening map', err);
        }
      );
    }
  };

  // 출석 체크 완료 핸들러
  const handleAttendanceComplete = () => {
    console.log('출석 체크 완료');
    setShouldAutoAttendance(false);
    // 모달은 열어둠 - 사용자가 직접 닫을 수 있도록
  };

  const handleLoginSuccess = async (user) => {
    console.log('로그인 성공:', user);

    // 착용 중인 아바타 먼저 로드 (깜빡임 방지)
    setIsChangingModel(true);
    try {
      const equippedAvatar = await shopService.getEquippedAvatar();
      if (equippedAvatar && equippedAvatar.shopItem && equippedAvatar.shopItem.modelUrl) {
        console.log('✅ 착용 중인 아바타 로드:', equippedAvatar.shopItem.modelUrl);
        setCharacterModelPathState(equippedAvatar.shopItem.modelUrl);
      } else {
        console.log('착용 중인 아바타 없음 - BaseCharacter 사용');
      }
    } catch (error) {
      console.error('착용 아바타 로드 실패:', error);
      // 실패 시 BaseCharacter 사용 (기본값)
    }

    // 로그인 상태 설정
    setIsLoggedIn(true);
    setShowLanding(false);
    setUsername(user.username || 'Guest');
    setUserId(user.id || String(Date.now()));
    setUserProfile(user); // 프로필 정보 저장 (selectedProfile, selectedOutline 포함)

    // 오늘 출석 체크 여부 확인
    const bothAttended = await attendanceService.checkBothAttendedToday();

    if (!bothAttended) {
      // 오늘 출석하지 않았으면 출석 체크 모달 자동 표시
      setShowEventModal(true);
      setShouldAutoAttendance(true);
    }

    // 서버에서 재화 정보 가져오기
    try {
      const currency = await currencyService.getCurrency();
      setSilverCoins(currency.silverCoins || 0);
      setGoldCoins(currency.goldCoins || 0);
      console.log('✅ 재화 정보 로드:', currency);
    } catch (error) {
      console.error('재화 정보 로드 실패:', error);
      // 실패 시 기본값 설정
      setSilverCoins(0);
      setGoldCoins(0);
    }

    // 로딩 완료
    setTimeout(() => {
      setIsChangingModel(false);
    }, 500);
  };

  // 프로필 업데이트 시 호출되는 함수
  const handleProfileUpdate = async (updatedProfile) => {
    try {
      // ProfileModal에서 전달받은 업데이트된 프로필 사용
      if (updatedProfile) {
        setUserProfile(updatedProfile);
        setUsername(updatedProfile.username); // 캐릭터 위 닉네임 즉시 업데이트
        console.log('✅ 프로필 업데이트 완료:', updatedProfile);

        // 멀티플레이어 서비스에 닉네임 변경 알림
        if (multiplayerService.isConnected()) {
          multiplayerService.updatePlayerInfo({
            username: updatedProfile.username,
            selectedProfile: updatedProfile.selectedProfile,
            selectedOutline: updatedProfile.selectedOutline
          });
        }
      }
    } catch (error) {
      console.error('Failed to update user profile:', error);
    }
  };

  // 설정 변경 핸들러
  const handleSettingsChange = (newSettings) => {
    setAppSettings(newSettings);
  };

  // 설정이 변경될 때 localStorage에 저장
  useEffect(() => {
    try {
      localStorage.setItem('appSettings', JSON.stringify(appSettings));
    } catch (error) {
      console.error('Failed to save app settings:', error);
    }
  }, [appSettings]);

  // 알림 서비스 구독
  useEffect(() => {
    // 초기 읽지 않은 알림 개수 설정
    setUnreadNotificationCount(notificationService.getUnreadCount());

    // 알림 변경 구독
    const unsubscribe = notificationService.subscribe((notifications, unreadCount) => {
      setUnreadNotificationCount(unreadCount);

      // 새 알림이 추가되면 토스트로 표시 (설정에 따라)
      if (appSettings.other?.showToastNotifications && notifications.length > 0) {
        const latestNotification = notifications[0];
        if (!latestNotification.read) {
          // 이미 토스트에 있는지 확인
          const alreadyShown = toastNotifications.some(t => t.id === latestNotification.id);
          if (!alreadyShown) {
            setToastNotifications(prev => [...prev, latestNotification]);
          }
        }
      }
    });

    return unsubscribe;
  }, [appSettings.other?.showToastNotifications]);

  // 친구 업데이트 이벤트 구독 (알림 생성)
  useEffect(() => {
    if (!isLoggedIn) return;

    const unsubscribe = multiplayerService.onFriendUpdate((data) => {
      console.log('친구 업데이트 이벤트:', data);

      if (data.type === 'FRIEND_REQUEST') {
        // 친구 요청 알림 생성
        notificationService.createFriendRequestNotification(
          data.requesterUsername,
          data.friendshipId,
          data.requesterId
        );
      } else if (data.type === 'FRIEND_ACCEPTED') {
        // 친구 수락 알림 생성
        notificationService.createFriendAcceptedNotification(data.acceptorUsername);
      }
    });

    return unsubscribe;
  }, [isLoggedIn]);

  // DM 메시지 이벤트 구독 (알림 생성)
  useEffect(() => {
    if (!isLoggedIn) return;

    const unsubscribe = multiplayerService.onDMMessage((data) => {
      console.log('DM 메시지 수신 (알림 생성):', data);

      // DM 메시지 알림 생성 (content 필드 사용)
      if (data.senderUsername && data.content) {
        notificationService.createChatNotification(
          data.senderUsername,
          data.content,
          data.senderId,
          data.senderProfileImagePath,
          data.senderOutlineImagePath
        );
        console.log('✅ DM 알림 생성 완료:', data.senderUsername);
      } else {
        console.error('❌ DM 알림 생성 실패 - 필드 누락:', data);
      }
    });

    return unsubscribe;
  }, [isLoggedIn]);

  // 게임 초대 이벤트 구독 (알림 생성)
  useEffect(() => {
    if (!isLoggedIn) return;

    console.log('🎮 게임 초대 이벤트 구독 시작, userId:', userId);

    minigameService.on('gameInvite', (data) => {
      console.log('🎮 게임 초대 수신:', data);

      // 게임 초대 알림 생성
      if (data && data.inviterUsername && data.gameName) {
        notificationService.createGameInviteNotification(
          data.inviterUsername,
          data.gameName,
          data.roomId,
          data.inviterId
        );
        console.log('✅ 게임 초대 알림 생성 완료:', data.inviterUsername, data.gameName);
      } else {
        console.error('❌ 게임 초대 알림 생성 실패 - 필드 누락:', data);
      }
    });

    // join result (ACK) 수신 핸들러
    minigameService.on('joinResult', (data) => {
      if (!data || data.payload == null) return;
      if (String(data.payload).startsWith('error')) {
        alert('게임 방 입장에 실패했습니다: ' + data.payload);
        // 실패일 경우 pending room reset
        setPendingJoinRoomId(null);
        // 방 목록 갱신 시도
        minigameService.requestRoomsList();
        setShowMinigameModal(false);
      } else if (data.payload === 'ok') {
        // 성공 시 모달을 대기방 모드로 열고 pending room을 설정하여 모달에서 자동으로 대기방으로 전환하도록 함
        setPendingJoinRoomId(data.roomId);
        setMinigameModalMode('waiting');
        setShowMinigameModal(true);
      }
    });

    // 전역 게임 이벤트 구독: 게임 시작/스폰 이벤트를 받으면 모달 열기
    minigameService.on('gameEvent', (evt) => {
      if (!evt) return;
      if (evt.type === 'gameStart' || evt.type === 'spawnTarget' || evt.type === 'reactionGo') {
        console.log('전역 gameEvent 수신, 모달 열기:', evt);
        // 모달이 닫혀 있다면 연다
        if (!showMinigameModal) {
          setPendingJoinRoomId(evt.roomId || null);
          setMinigameModalMode('waiting');
          setShowMinigameModal(true);
        }
      }
    });

    return () => {
      console.log('🎮 게임 초대 이벤트 구독 해제');
      minigameService.on('gameInvite', null);
      minigameService.on('joinResult', null);
    };
  }, [isLoggedIn, userId]);

  // 토스트 알림 닫기 핸들러
  const handleToastClose = (notificationId) => {
    setToastNotifications(prev => prev.filter(n => n.id !== notificationId));
  };

  // 토스트 알림 수락 핸들러 (친구 요청, 게임 초대 등)
  const handleToastAccept = async (notification) => {
    console.log('알림 수락:', notification);

    if (notification.type === 'friend_request') {
      // 친구 요청 수락 로직
      try {
        await friendService.acceptFriendRequest(notification.data.friendshipId);
        console.log('친구 요청 수락 완료:', notification.data.requesterUsername);
        // 알림을 읽음으로 표시
        notificationService.markAsRead(notification.id);
      } catch (error) {
        console.error('친구 요청 수락 실패:', error);
        alert('친구 요청 수락에 실패했습니다.');
      }

    } else if (notification.type === 'game_invite') {
      // 게임 초대 수락 - 게임 방 입장 및 초대자 근처로 이동
      try {
        const { roomId, inviterId } = notification.data;
        console.log('🎮 게임 초대 수락:', { roomId, inviterId });

        // 1. 초대자의 위치 찾기
        const inviterPlayer = Object.values(otherPlayers).find(
          player => String(player.userId) === String(inviterId)
        );

        if (inviterPlayer && inviterPlayer.position) {
          // 2. 초대자 근처로 캐릭터 텔레포트 (랜덤 오프셋, 같은 높이)
          const randomAngle = Math.random() * Math.PI * 2;
          const distance = 3 + Math.random() * 2; // 3-5 유닛 거리
          const offsetX = Math.cos(randomAngle) * distance;
          const offsetZ = Math.sin(randomAngle) * distance;

          const targetPosition = [
            inviterPlayer.position[0] + offsetX,
            inviterPlayer.position[1], // 같은 높이로
            inviterPlayer.position[2] + offsetZ
          ];

          console.log('📍 초대자 위치:', inviterPlayer.position);
          console.log('📍 이동 목표 위치:', targetPosition);

          // characterRef를 통해 텔레포트
          if (characterRef.current?.teleportTo) {
            characterRef.current.teleportTo(targetPosition);
          }
        } else {
          console.warn('⚠️ 초대자를 찾을 수 없음 (오프라인일 수 있음)');
        }

        // 3. 게임 방 입장 (사용자 프로필 정보와 함께)
        console.log('🎮 minigameService 연결 상태:', minigameService.connected);

        // 낙관적 UI: 초대 수락 즉시 대기방으로 전환 (서버 ACK를 기다리지 않음)
        console.log('🎮 초대 수락 - 대기방 UI 즉시 전환:', roomId);
        setPendingJoinRoomId(roomId);
        setMinigameModalMode('waiting');
        setShowMinigameModal(true);

        try {
          if (!minigameService.connected) {
            console.warn('⚠️ minigameService가 연결되지 않음, 연결 시도...');
            await minigameService.connect(userId, username, 5000);
          }

          console.log('✅ 게임 방 입장 요청:', {
            roomId,
            level: userProfile?.level || 1,
            selectedProfile: userProfile?.selectedProfile?.id,
            selectedOutline: userProfile?.selectedOutline?.id
          });

          minigameService.joinRoom(
            roomId,
            userProfile?.level || 1,
            userProfile?.selectedProfile?.imagePath,
            userProfile?.selectedOutline?.imagePath
          );

          // 최신 방 목록 요청하여 modal이 방 정보를 찾을 수 있게 함
          minigameService.requestRoomsList();
        } catch (err) {
          console.error('❌ 게임 방 입장 실패 (초대 수락 처리 중):', err);
          alert('게임 방 입장에 실패했습니다. 다시 시도해주세요.');
          // 실패 시 pending reset
          setPendingJoinRoomId(null);
          setShowMinigameModal(false);
        }

        // 5. 알림을 읽음으로 표시
        notificationService.markAsRead(notification.id);
      } catch (error) {
        console.error('게임 방 입장 실패:', error);
        alert('게임 방 입장에 실패했습니다. 다시 시도해주세요.');
      }
    }
  };

  // 토스트 알림 거절 핸들러
  const handleToastReject = async (notification) => {
    console.log('알림 거절:', notification);

    if (notification.type === 'friend_request') {
      // 친구 요청 거절 로직
      try {
        await friendService.rejectFriendRequest(notification.data.friendshipId);
        console.log('친구 요청 거절 완료:', notification.data.requesterUsername);
        // 알림 삭제
        notificationService.deleteNotification(notification.id);
      } catch (error) {
        console.error('친구 요청 거절 실패:', error);
        alert('친구 요청 거절에 실패했습니다.');
      }
    } else if (notification.type === 'game_invite') {
      // 게임 초대 거절 - 알림만 삭제
      console.log('게임 초대 거절:', notification.data);
      notificationService.deleteNotification(notification.id);
    }
  };

  // 토스트 알림 클릭 핸들러 (DM 알림 등)
  const handleToastClick = (notification) => {
    console.log('알림 클릭:', notification);

    if (notification.type === 'chat') {
      // DM 알림 클릭 시 PhoneUI의 채팅창 열기
      const friendInfo = {
        id: notification.data.senderId,
        friendId: notification.data.senderId,
        friendName: notification.data.senderUsername,
        profileImagePath: notification.data.senderProfileImagePath,
        outlineImagePath: notification.data.senderOutlineImagePath,
      };
      setPhoneInitialFriend(friendInfo);
      setShowPhoneUI(true);
      // 알림 삭제
      notificationService.deleteNotification(notification.id);
    }
  };

  // 재화 업데이트 함수 (다른 컴포넌트에서 호출 가능)
  const updateCurrency = async () => {
    try {
      const currency = await currencyService.getCurrency();
      setSilverCoins(currency.silverCoins || 0);
      setGoldCoins(currency.goldCoins || 0);
      console.log('✅ 재화 업데이트:', currency);
    } catch (error) {
      console.error('재화 업데이트 실패:', error);
    }
  };

  const handleLogout = async () => {
    try {
      // Call logout API to remove user from active list
      await authService.logout();
    } catch (error) {
      console.error('Logout API error:', error);
    }

    // Disconnect from multiplayer service
    multiplayerService.disconnect();
    setIsLoggedIn(false);
    setShowLanding(true);
    setUsername('');
    setUserId('');
    setUserProfile(null);
    setOtherPlayers({});
    setOnlineCount(0);
    setSilverCoins(0);
    setGoldCoins(0);
  };

  // 채팅 메시지 처리 함수 (GlobalChat에서 호출됨)
  const handleChatMessage = useCallback((data) => {
    if (String(data.userId) === String(userId)) {
      // My own message
      // 이전 타이머가 있으면 취소
      if (myMessageTimerRef.current) {
        clearTimeout(myMessageTimerRef.current);
      }

      setMyChatMessage(data.message);

      // 새 타이머 설정 - 5초 후 삭제
      myMessageTimerRef.current = setTimeout(() => {
        setMyChatMessage('');
        myMessageTimerRef.current = null;
      }, 5000);
    } else {
      // Other player's message
      // 이전 타이머가 있으면 취소
      if (playerMessageTimersRef.current[data.userId]) {
        clearTimeout(playerMessageTimersRef.current[data.userId]);
      }

      setPlayerChatMessages((prev) => ({
        ...prev,
        [data.userId]: {
          message: data.message,
          timestamp: Date.now()
        }
      }));

      // 새 타이머 설정 - 5초 후 삭제
      playerMessageTimersRef.current[data.userId] = setTimeout(() => {
        setPlayerChatMessages((prev) => {
          const updated = { ...prev };
          delete updated[data.userId];
          return updated;
        });
        delete playerMessageTimersRef.current[data.userId];
      }, 5000);
    }
  }, [userId]);

  // 플레이어 우클릭 핸들러
  const handlePlayerRightClick = useCallback((event, playerData) => {
    // Three.js 이벤트는 nativeEvent를 통해 브라우저 이벤트에 접근
    const nativeEvent = event.nativeEvent || event;

    if (nativeEvent.preventDefault) {
      nativeEvent.preventDefault();
    }

    // 마우스 위치를 화면 좌표로 변환
    setContextMenu({
      position: { x: nativeEvent.clientX, y: nativeEvent.clientY },
      playerData: playerData
    });
  }, []);

  // 프로필 보기
  const handleViewProfile = (playerData) => {
    console.log('프로필 보기:', playerData);
    setOtherPlayerProfile({
      userId: playerData.userId,
      username: playerData.username
    });
  };

  // 친구 추가
  const handleAddFriend = async (playerData) => {
    try {
      console.log('친구 추가:', playerData);
      const result = await friendService.sendFriendRequest(playerData.username);
      setNotification({
        message: result.message || `${playerData.username}에게 친구 요청을 보냈습니다.`,
        type: 'success'
      });
    } catch (error) {
      console.error('친구 요청 실패:', error);
      console.error('에러 응답 데이터:', error.response?.data);
      console.error('에러 상태 코드:', error.response?.status);
      const errorMessage = error.response?.data?.message || '친구 요청에 실패했습니다.';
      setNotification({
        message: errorMessage,
        type: 'error'
      });
    }
  };

  // 게임 트리거 진입/이탈 핸들러
  const handleGameTriggerEnter = () => {
    console.log('🎮 게임 트리거 진입! 아이콘 표시');
    setShowGameIcon(true);
  };

  const handleGameTriggerExit = () => {
    console.log('🎮 게임 트리거 이탈! 아이콘 숨김');
    setShowGameIcon(false);
  };

  // 미니게임 아이콘 클릭 핸들러
  const handleGameIconClick = () => {
    console.log('🎮 미니게임 로비 아이콘 클릭');
    setMinigameModalMode('lobby'); // 로비 모드로 설정
    setShowMinigameModal(true);
  };

  // 방 생성 아이콘 클릭 핸들러
  const handleCreateRoomIconClick = () => {
    console.log('🎮 방 생성 아이콘 클릭');
    setShowMinigameModal(true);
    setMinigameModalMode('create'); // 방 생성 모드로 열기
  };

  // Set up multiplayer callbacks (once)
  useEffect(() => {
    multiplayerService.onPlayerJoin((data) => {
      // 중복 로그인 체크
      if (data.action === 'duplicate') {
        // 자신의 중복 로그인 시도인지 확인
        if (isLoggedIn && String(data.userId) === String(userId)) {
          alert('현재 접속 중인 아이디입니다.');
          handleLogout();
        }
        return;
      }

      // 자신의 join 이벤트는 무시 (multiplayerService의 userId와 비교)
      if (String(data.userId) === String(multiplayerService.userId)) {
        console.log('Ignoring own join event:', data.userId);
        return;
      }

      console.log('Adding other player:', data.username, data.userId);

      // Update otherPlayers state
      setOtherPlayers((prev) => ({
        ...prev,
        [data.userId]: {
          userId: data.userId,
          username: data.username,
          position: [5, 1, 5], // 지면 위치
          rotationY: 0,
          animation: 'idle'
        }
      }));

      // Notify GlobalChat
      setPlayerJoinEvent({ ...data, timestamp: Date.now() });
    });

    multiplayerService.onPlayerLeave((data) => {
      setOtherPlayers((prev) => {
        const updated = { ...prev };
        delete updated[data.userId];
        return updated;
      });

      // Notify GlobalChat
      setPlayerLeaveEvent({ ...data, timestamp: Date.now() });
    });

    multiplayerService.onPositionUpdate((data) => {
      setOtherPlayers((prev) => ({
        ...prev,
        [data.userId]: {
          userId: data.userId,
          username: data.username,
          position: [data.x, data.y, data.z],
          rotationY: data.rotationY,
          animation: data.animation,
          modelPath: data.modelPath || '/resources/Ultimate Animated Character Pack - Nov 2019/glTF/BaseCharacter.gltf',
          isChangingAvatar: data.isChangingAvatar || false
        }
      }));
    });

    // Online count update handler
    multiplayerService.onOnlineCountUpdate((count) => {
      setOnlineCount(count);
    });
  }, []); // 콜백은 한 번만 등록

  // Connect to multiplayer service when login state changes
  useEffect(() => {
    // Connect as observer if not logged in, or as player if logged in
    if (isLoggedIn && userId && username) {
      // console.log('🔗 Connecting to multiplayer service as player...', { userId, username });
      multiplayerService.connect(userId, username);

      // 미니게임 서비스도 연결 (게임 초대를 받기 위해)
      console.log('🎮 Connecting to minigame service...', { userId, username });
      minigameService.connect(userId, username);
    } else if (!isLoggedIn) {
      // Connect as observer (anonymous viewer)
      // console.log('👀 Connecting to multiplayer service as observer...');
      const observerId = 'observer_' + Date.now();
      multiplayerService.connect(observerId, 'Observer', true); // true = observer mode
    }

    // Cleanup on unmount - only disconnect when component unmounts
    return () => {
      // Only disconnect on actual unmount, not on dependency changes
    };
  }, [isLoggedIn, userId, username]);

  // MapGamePageNew에서 보낸 minigame 모달 열기 신호 처리 (커스텀 이벤트)
  useEffect(() => {
    const handleShowMinigameModal = (event) => {
      const mode = event.detail?.mode;
      console.log('🎮 Minigame modal 신호 받음 (커스텀 이벤트):', mode);
      
      if (mode === 'create') {
        setMinigameModalMode('create');
      } else if (mode === 'lobby') {
        setMinigameModalMode('lobby');
      }
      
      setShowMinigameModal(true);
    };

    window.addEventListener('showMinigameModal', handleShowMinigameModal);
    
    return () => {
      window.removeEventListener('showMinigameModal', handleShowMinigameModal);
    };
  }, []);

  // MapGamePageNew에서 보낸 minigame 모달 열기 신호 처리

  // Cleanup on window close or refresh
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (isLoggedIn) {
        // Disconnect WebSocket - this will trigger SessionDisconnectEvent on server
        multiplayerService.disconnect();

        // Send beacon to logout endpoint (non-blocking)
        const token = authService.getToken();
        if (token) {
          const url = `${process.env.REACT_APP_API_URL || 'http://localhost:8080'}/api/auth/logout`;
          const blob = new Blob([JSON.stringify({})], { type: 'application/json' });
          navigator.sendBeacon(url, blob);
        }
      }
    };

    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [isLoggedIn]);

  // 페이지 로드 시 로그인 상태 복원 (localStorage에서)
  useEffect(() => {
    const token = authService.getToken();
    const user = authService.getCurrentUser();

    if (token && user) {
      console.log('[App] 토큰 발견 - 서버 유효성 검증 시작:', user.username);
      setIsChangingModel(true); // 로딩 시작

      // 서버에서 토큰 유효성 확인
      authService.fetchCurrentUser()
        .then(validUser => {
          if (validUser) {
            console.log('[App] ✅ 토큰 유효 - 사용자 정보 로드');
            // 사용자 정보 저장 (아직 로그인 상태는 설정하지 않음)
            setUsername(validUser.username || 'Guest');
            setUserId(validUser.id || String(Date.now()));
            setUserProfile(validUser);

            // 재화 정보 로드
            return Promise.all([
              currencyService.getCurrency(),
              shopService.getEquippedAvatar()
            ]);
          } else {
            console.log('[App] ❌ 토큰 무효 - 로그아웃 처리');
            authService.logout();
            setIsChangingModel(false);
            return null;
          }
        })
        .then(results => {
          if (results) {
            const [currency, equippedAvatar] = results;

            // 재화 정보 설정
            if (currency) {
              setSilverCoins(currency.silverCoins || 0);
              setGoldCoins(currency.goldCoins || 0);
              console.log('[App] ✅ 재화 정보 복원:', currency);
            }

            // 착용 중인 아바타 설정
            if (equippedAvatar && equippedAvatar.shopItem && equippedAvatar.shopItem.modelUrl) {
              console.log('[App] ✅ 착용 중인 아바타 복원:', equippedAvatar.shopItem.modelUrl);
              setCharacterModelPathState(equippedAvatar.shopItem.modelUrl);
            } else {
              console.log('[App] 착용 중인 아바타 없음 - BaseCharacter 사용');
            }

            // 모든 데이터 로드 완료 후 로그인 상태 설정
            setIsLoggedIn(true);
            setShowLanding(false);

            // 로딩 완료
            setTimeout(() => {
              setIsChangingModel(false);
            }, 500);
          }
        })
        .catch(error => {
          console.error('[App] ❌ 로그인 복원 실패:', error);
          // 401 에러 (토큰 만료) 또는 네트워크 에러
          if (error.response?.status === 401) {
            console.log('[App] 토큰 만료 - 로그아웃');
          }
          authService.logout();
          setSilverCoins(0);
          setGoldCoins(0);
        });
    } else {
      console.log('[App] 로그인 상태 없음 - 랜딩 페이지 표시');
    }
  }, []); // 컴포넌트 마운트 시 1회만 실행






  // Canvas props 최적화: 객체를 메모이제이션하여 불필요한 재렌더링 방지
  const glConfig = useMemo(() => ({
    alpha: true,
    antialias: true,
    preserveDrawingBuffer: true
  }), []);

  const cameraConfig = useMemo(() => ({
    position: [-0.00, 28.35, 19.76],
    rotation: [-0.96, -0.00, -0.00]
  }), []);

  return (
    <div className="App">
      {/* Mapbox 배경 및 Three.js 오버레이 */}
      {isMapFull && (
        <Mapbox3D onMapReady={handleMapReady} isFull={isMapFull} />
      )}

      {/* 프로필 아바타 (로그인한 사용자만 표시) */}
      {isLoggedIn && (
        <>
          <button
            className={`profile-avatar-button ${isMapFull ? 'bottom-right' : 'top-left'}`}
            onClick={() => setShowProfileModal(true)}
            title="프로필"
          >
            <ProfileAvatar
              profileImage={userProfile?.selectedProfile}
              outlineImage={userProfile?.selectedOutline}
              size={150}
            />
          </button>

          {/* 재화 표시 (프로필 아바타 우측) */}
          <div className={`currency-display-wrapper ${isMapFull ? 'currency-display-bottom-right' : 'currency-display-top-left'}`}>
            <CurrencyDisplay
              silverCoins={Number(silverCoins) || 0}
              goldCoins={Number(goldCoins) || 0}
              onChargeGold={() => {
                setGoldChargeModalTab('charge');
                setShowGoldChargeModal(true);
              }}
              onExchangeSilver={() => {
                setGoldChargeModalTab('exchange');
                setShowGoldChargeModal(true);
              }}
            />
          </div>
        </>
      )}

      {/* 아이콘 메뉴 (로그인한 사용자만 표시) */}
      {isLoggedIn && !isMapFull && (
        <div className={`icon-menu-container ${isMenuExpanded ? 'expanded' : ''}`}>
          {/* 토글 화살표 */}
          <button
            className="menu-toggle-arrow"
            onClick={() => setIsMenuExpanded(!isMenuExpanded)}
          >
            <img
              src={isMenuExpanded ? '/resources/Icon/rightarrow.png' : '/resources/Icon/leftarrow.png'}
              alt={isMenuExpanded ? 'Close' : 'Open'}
            />
          </button>

          {/* 확장 시 보이는 아이콘들 */}
          <div className={`secondary-icons ${isMenuExpanded ? 'show' : 'hide'}`}>
            <button
              className="icon-button notification-icon-button"
              onClick={() => setShowNotificationModal(true)}
              title="알림"
            >
              <img src="/resources/Icon/Alarm-icon.png" alt="Alarm" />
              {unreadNotificationCount > 0 && (
                <span className="notification-badge">{unreadNotificationCount}</span>
              )}
            </button>
            <button className="icon-button" onClick={() => setShowPhoneUI(true)} title="모바일 (친구/채팅)">
              <img src="/resources/Icon/Mobile-icon.png" alt="Mobile" />
            </button>
            <button className="icon-button" onClick={() => setShowEventModal(true)} title="이벤트">
              <img src="/resources/Icon/Event-icon.png" alt="Event" />
            </button>
            <button className="icon-button" onClick={() => setShowSettingModal(true)} title="설정">
              <img src="/resources/Icon/Setting-icon.png" alt="Setting" />
            </button>
            <button className="icon-button" onClick={() => setShowShopModal(true)} title="상점">
              <img src="/resources/Icon/Shop-icon.png" alt="Shop" />
            </button>
            <button className="icon-button" onClick={() => setShowInventoryModal(true)} title="인벤토리">
              <img src="/resources/Icon/Inventory-icon.png" alt="Inventory" />
            </button>
          </div>

          {/* 게시판 아이콘 */}
          <button className="icon-button primary-board" onClick={() => setShowBoardModal(true)} title="게시판">
            <img src="/resources/Icon/Board-icon.png" alt="Board" />
          </button>

          {/* 지도 아이콘 */}
          <button className="icon-button primary-map" onClick={toggleMapFull} title="지도">
            <img src="/resources/Icon/Map-icon.png" alt="Map" />
          </button>
        </div>
      )}

      {/* Token warning if user opens map but token missing */}
      {isMapFull && !mapboxToken && (
        <div className="map-token-warning">Mapbox token not set. Fill `REACT_APP_MAPBOX_TOKEN` in your `.env`.</div>
      )}

      {/* 3D 배경 (항상 렌더링) - 지도 위에 오버레이로 렌더링됩니다 */}
      <div className="three-overlay">
        <Canvas
          className="three-canvas"
          camera={cameraConfig}
          shadows={appSettings.graphics?.shadows !== 'off'}
          dpr={appSettings.graphics?.quality === 'advanced' ? window.devicePixelRatio : 1}
          gl={glConfig}
          style={{ width: '100%', height: '100%' }}
        >
          <ambientLight intensity={0.5} />
          <directionalLight
            position={[50, 50, 25]}
            intensity={6}
            castShadow={appSettings.graphics?.shadows !== 'off'}
            shadow-mapSize-width={8192}
            shadow-mapSize-height={8192}
            shadow-camera-far={1000}
            shadow-camera-left={-500}
            shadow-camera-right={500}
            shadow-camera-top={500}
            shadow-camera-bottom={-500}
            shadow-bias={-0.0001}
            shadow-normalBias={0.02}
            shadow-radius={4}
          />
          {/* Sun visual */}
          <mesh position={[50, 50, 25]}>
            <sphereGeometry args={[3, 16, 16]} />
            <meshBasicMaterial color="#FDB813" />
          </mesh>

          <Suspense fallback={null}>
            <Physics gravity={[0, -40, 0]}>
              {/* 로그인 후에만 캐릭터 표시 */}
              {isLoggedIn && (
                <>
                  {/* 지도 모드: MapCharacterController 사용 */}
                  {isMapFull ? (
                    <MapCharacterController
                      characterRef={characterRef}
                      isMovementDisabled={shouldBlockMovement}
                      username={username}
                      userId={userId}
                      multiplayerService={multiplayerService}
                      chatMessage={myChatMessage}
                      onPositionUpdate={handleMapCharacterPositionUpdate}
                      modelPath={characterModelPath}
                      isChangingAvatar={isChangingModel}
                    />
                  ) : (
                    /* Level1 모드: 기존 Character 사용 */
                    <Character
                      characterRef={characterRef}
                      initialPosition={initialPosition}
                      isMovementDisabled={shouldBlockMovement}
                      username={username}
                      userId={userId}
                      userEmail={userProfile?.email}
                      multiplayerService={multiplayerService}
                      isMapFull={isMapFull}
                      onPositionUpdate={handleCharacterPositionUpdate}
                      chatMessage={myChatMessage}
                      modelPath={characterModelPath}
                      isChangingAvatar={isChangingModel}
                    />
                  )}
                  <CameraLogger />
                </>
              )}

              {/* Render other players - 로그인 여부와 관계없이 항상 표시 (observer 제외) */}
              {Object.values(otherPlayers)
                .filter((player) => !String(player.userId).startsWith('observer_'))
                .map((player) => (
                  <OtherPlayer
                    key={player.userId}
                    userId={player.userId}
                    username={player.username}
                    position={player.position}
                    rotationY={player.rotationY}
                    animation={player.animation}
                    chatMessage={playerChatMessages[player.userId]?.message}
                    onRightClick={handlePlayerRightClick}
                    modelPath={player.modelPath}
                    isChangingAvatar={player.isChangingAvatar}
                  />
                ))}

              {/* CameraController는 항상 렌더링 (로그인 전: MainCamera, 로그인 후: Character) */}
              <CameraController
                characterRef={characterRef}
                mainCameraRef={mainCameraRef}
                isLoggedIn={isLoggedIn}
                inPersonalRoom={false} // TODO: 개인 방 진입 시 true로 변경
              />
              {/* 지도 모드일 때만 MapFloor 렌더링 */}
              {isMapFull && <MapFloor />}
              {/* Level1은 지도 모드가 아닐 때만 렌더링 */}
              {!isMapFull && (
                <Level1
                  characterRef={characterRef}
                  mainCameraRef={mainCameraRef}
                  onGameTriggerEnter={handleGameTriggerEnter}
                  onGameTriggerExit={handleGameTriggerExit}
                />
              )}
            </Physics>
          </Suspense>
        </Canvas>
      </div>

      {/* 랜딩 페이지 오버레이 (지도 전체화면일 때는 숨김) */}
      {showLanding && !isMapFull && (
        <LandingPage onLoginSuccess={handleLoginSuccess} />
      )}

      {/* 맵 전체화면일 때 뒤로가기 버튼 (왼쪽 상단) */}
      {isMapFull && (
        <button className="map-back-button prominent" onClick={toggleMapFull}>Back</button>
      )}

      {/* 게시판 모달 */}
      {showBoardModal && (
        <BoardModal onClose={() => setShowBoardModal(false)} />
      )}

      {/* 프로필 모달 */}
      {showProfileModal && (
        <ProfileModal
          onClose={() => setShowProfileModal(false)}
          onLogout={handleLogout}
          onProfileUpdate={handleProfileUpdate}
        />
      )}

      {/* 설정 모달 */}
      {showSettingModal && (
        <SettingModal
          onClose={() => setShowSettingModal(false)}
          onSettingsChange={handleSettingsChange}
        />
      )}

      {/* 알림 모달 */}
      {showNotificationModal && (
        <NotificationModal onClose={() => setShowNotificationModal(false)} />
      )}

      {/* 실시간 알림 토스트 (화면 우측 상단에 표시) */}
      {appSettings.other?.showToastNotifications && toastNotifications.length > 0 && (
        <div className="notification-toast-container">
          {toastNotifications.map((notification) => (
            <NotificationToast
              key={notification.id}
              notification={notification}
              onClose={() => handleToastClose(notification.id)}
              onAccept={handleToastAccept}
              onReject={handleToastReject}
              onClick={handleToastClick}
              autoCloseDelay={5000}
            />
          ))}
        </div>
      )}

      {/* 이벤트 모달 */}
      {showEventModal && (
        <EventModal
          onClose={() => setShowEventModal(false)}
          shouldAutoAttendance={shouldAutoAttendance}
          onAttendanceComplete={handleAttendanceComplete}
          onCoinsUpdate={(silver, gold) => {
            setSilverCoins(silver);
            setGoldCoins(gold);
          }}
        />
      )}

      {/* 미니게임 모달 */}
      {showMinigameModal && (
        <MinigameModal
          onClose={() => {
            setShowMinigameModal(false);
            setMinigameModalMode('lobby'); // 모달 닫을 때 로비 모드로 초기화
            setPendingJoinRoomId(null);
          }}
          userProfile={userProfile}
          onlinePlayers={otherPlayers}
          initialMode={minigameModalMode}
          initialRoomId={pendingJoinRoomId}
        />
      )}

      {/* Phone UI (친구목록/채팅) */}
      <PhoneUI
        isOpen={showPhoneUI}
        onClose={() => setShowPhoneUI(false)}
        userId={userId}
        username={username}
        onlinePlayers={otherPlayers}
        initialFriend={phoneInitialFriend}
        onInitialFriendOpened={() => setPhoneInitialFriend(null)}
      />

      {/* 제재 알림 (로그인한 사용자만) */}
      {isLoggedIn && <SuspensionNotification />}

      {/* 전체 채팅 (로그인한 사용자만, 맵 전체화면 아닐 때만 표시) */}
      {isLoggedIn && !isMapFull && (
        <GlobalChat
          isVisible={true}
          username={username}
          userId={userId}
          onlineCount={onlineCount}
          playerJoinEvent={playerJoinEvent}
          playerLeaveEvent={playerLeaveEvent}
          onInputFocusChange={setIsChatInputFocused}
          onChatMessage={handleChatMessage}
          isPhoneUIOpen={showPhoneUI}
        />
      )}

      {/* 컨텍스트 메뉴 (우클릭) */}
      {contextMenu && (
        <ContextMenu
          position={contextMenu.position}
          playerData={contextMenu.playerData}
          onClose={() => setContextMenu(null)}
          onViewProfile={handleViewProfile}
          onAddFriend={handleAddFriend}
        />
      )}

      {/* 다른 플레이어 프로필 모달 */}
      {otherPlayerProfile && (
        <OtherPlayerProfileModal
          userId={otherPlayerProfile.userId}
          username={otherPlayerProfile.username}
          onClose={() => setOtherPlayerProfile(null)}
          onAddFriend={handleAddFriend}
        />
      )}

      {/* 알림 */}
      {notification && (
        <Notification
          message={notification.message}
          type={notification.type}
          onClose={() => setNotification(null)}
        />
      )}

      {/* 게임 아이콘 (cliff_block_rock002 위에 있을 때만 표시) */}
      {isLoggedIn && !isMapFull && (
        <GameIcon
          visible={showGameIcon}
          onClick={handleGameIconClick}
          onCreateRoom={handleCreateRoomIconClick}
        />
      )}

      {showShopModal && (
        <ShopModal
          onClose={() => setShowShopModal(false)}
          userCoins={{ silver: silverCoins, gold: goldCoins }}
          onCoinsUpdate={(silver, gold) => {
            setSilverCoins(silver);
            setGoldCoins(gold);
          }}
          setCharacterModelPath={(newModelPath) => {
            console.log('🟡 [App.js] setCharacterModelPath 호출됨!');
            console.log('🟡 [App.js] 새 모델 경로:', newModelPath);

            setIsChangingModel(true);
            setCharacterModelPathState(newModelPath);

            console.log('🟡 [App.js] 로딩 화면 시작 (1.5초)');
            setTimeout(() => {
              setIsChangingModel(false);
              console.log('🟡 [App.js] 로딩 화면 종료');
            }, 1500);
          }}
        />
      )}

      {showInventoryModal && (
        <InventoryModal
          onClose={() => setShowInventoryModal(false)}
          setCharacterModelPath={(newModelPath) => {
            console.log('🟡 [App.js] setCharacterModelPath 호출됨 (from Inventory)!');
            console.log('🟡 [App.js] 새 모델 경로:', newModelPath);

            setIsChangingModel(true);
            setCharacterModelPathState(newModelPath);

            console.log('🟡 [App.js] 로딩 화면 시작 (1.5초)');
            setTimeout(() => {
              setIsChangingModel(false);
              console.log('🟡 [App.js] 로딩 화면 종료');
            }, 1500);
          }}
        />
      )}

      {showGoldChargeModal && (
        <GoldChargeModal
          onClose={() => setShowGoldChargeModal(false)}
          initialTab={goldChargeModalTab}
          onChargeSuccess={(result) => {
            console.log('[App] 결제 성공 콜백:', result);
            // 재화 업데이트
            if (result.remainingGoldCoins !== undefined) {
              setGoldCoins(result.remainingGoldCoins);
            }
            // 서버에서 최신 재화 정보 다시 가져오기
            updateCurrency();
          }}
        />
      )}

      {/* 캐릭터 모델 변경 로딩 오버레이 */}
      {isChangingModel && (
        <div className="character-loading-overlay">
          <div className="loading-spinner"></div>
          <div className="loading-text">Changing Avatar...</div>
        </div>
      )}
    </div>
  );
}

export default App;
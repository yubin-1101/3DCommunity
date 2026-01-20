package com.community.config;

import com.community.model.*;
import com.community.model.enums.ItemType;
import com.community.model.enums.UnlockConditionType;
import com.community.repository.BoardRepository;
import com.community.repository.ProfileItemRepository;
import com.community.repository.UserProfileItemRepository;
import com.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final BoardRepository boardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProfileItemRepository profileItemRepository;
    private final UserProfileItemRepository userProfileItemRepository;

    @Override
    @Transactional
    public void run(String... args) {
        long startTime = System.currentTimeMillis();

        // 게시판 초기화 (배치)
        initializeBoards();

        // 계정 초기화 (배치)
        initializeAccounts();

        // 기본 프로필 아이템 생성
        initializeProfileItems();

        log.info("DataInitializer 완료: {}ms", System.currentTimeMillis() - startTime);
    }

    private void initializeBoards() {
        List<Board> boardsToSave = new ArrayList<>();

        if (!boardRepository.existsByName("자유게시판")) {
            boardsToSave.add(Board.builder()
                    .name("자유게시판")
                    .description("자유롭게 글을 쓰는 공간입니다.")
                    .category(Board.BoardCategory.FREE)
                    .build());
        }

        if (!boardRepository.existsByName("공지사항")) {
            boardsToSave.add(Board.builder()
                    .name("공지사항")
                    .description("중요한 공지사항을 확인하세요.")
                    .category(Board.BoardCategory.FREE)
                    .build());
        }

        if (!boardsToSave.isEmpty()) {
            boardRepository.saveAll(boardsToSave);
            log.info("게시판 {} 개 생성 완료", boardsToSave.size());
        }
    }

    private void initializeAccounts() {
        List<User> usersToSave = new ArrayList<>();

        if (!userRepository.existsByEmail("test@test.com")) {
            usersToSave.add(User.builder()
                    .username("테스트유저")
                    .email("test@test.com")
                    .password(passwordEncoder.encode("test1234"))
                    .role(Role.ROLE_USER)
                    .build());
        }

        if (!userRepository.existsByEmail("admin@admin.com")) {
            usersToSave.add(User.builder()
                    .username("관리자")
                    .email("admin@admin.com")
                    .password(passwordEncoder.encode("admin1234"))
                    .role(Role.ROLE_DEVELOPER)
                    .build());
        }

        if (!userRepository.existsByEmail("manager@manager.com")) {
            usersToSave.add(User.builder()
                    .username("사용자관리자")
                    .email("manager@manager.com")
                    .password(passwordEncoder.encode("manager1234"))
                    .role(Role.ROLE_ADMIN)
                    .build());
        }

        if (!usersToSave.isEmpty()) {
            userRepository.saveAll(usersToSave);
            log.info("계정 {} 개 생성 완료", usersToSave.size());
        }
    }

    private void initializeProfileItems() {
        // 이미 초기화되었는지 빠르게 확인
        if (profileItemRepository.findByItemCode("base-profile1").isPresent()) {
            log.info("프로필 아이템 이미 초기화됨 - 스킵");
            return;
        }

        List<ProfileItem> itemsToSave = new ArrayList<>();

        // 기본 프로필 이미지
        itemsToSave.add(createProfileItem("base-profile1", "기본 프로필 1", "/resources/Profile/base-profile1.png", ItemType.PROFILE, true, UnlockConditionType.NONE, null, 1));
        itemsToSave.add(createProfileItem("base-profile2", "기본 프로필 2", "/resources/Profile/base-profile2.png", ItemType.PROFILE, true, UnlockConditionType.NONE, null, 2));
        itemsToSave.add(createProfileItem("base-profile3", "기본 프로필 3", "/resources/Profile/base-profile3.png", ItemType.PROFILE, true, UnlockConditionType.NONE, null, 3));

        // 기본 테두리
        itemsToSave.add(createProfileItem("base-outline1", "기본 테두리 1", "/resources/ProfileOutline/base-outline1.png", ItemType.OUTLINE, true, UnlockConditionType.NONE, null, 1));
        itemsToSave.add(createProfileItem("base-outline2", "기본 테두리 2", "/resources/ProfileOutline/base-outline2.png", ItemType.OUTLINE, true, UnlockConditionType.NONE, null, 2));
        itemsToSave.add(createProfileItem("base-outline3", "기본 테두리 3", "/resources/ProfileOutline/base-outline3.png", ItemType.OUTLINE, true, UnlockConditionType.NONE, null, 3));

        // 잠금 테두리
        itemsToSave.add(createProfileItem("base-outline4", "골드 테두리", "/resources/ProfileOutline/base-outline4.png", ItemType.OUTLINE, false, UnlockConditionType.POST_COUNT, "{\"value\": 10, \"description\": \"게시글 10개 작성\"}", 4));
        itemsToSave.add(createProfileItem("base-outline5", "실버 테두리", "/resources/ProfileOutline/base-outline5.png", ItemType.OUTLINE, false, UnlockConditionType.COMMENT_COUNT, "{\"value\": 50, \"description\": \"댓글 50개 작성\"}", 5));
        itemsToSave.add(createProfileItem("base-outline6", "다이아몬드 테두리", "/resources/ProfileOutline/base-outline6.png", ItemType.OUTLINE, false, UnlockConditionType.LOGIN_DAYS, "{\"value\": 7, \"description\": \"7일 연속 로그인\"}", 6));

        profileItemRepository.saveAll(itemsToSave);
        log.info("프로필 아이템 {} 개 생성 완료", itemsToSave.size());
    }

    private ProfileItem createProfileItem(String itemCode, String itemName, String imagePath,
            ItemType itemType, boolean isDefault, UnlockConditionType conditionType,
            String conditionValue, int displayOrder) {
        ProfileItem item = new ProfileItem();
        item.setItemCode(itemCode);
        item.setItemName(itemName);
        item.setImagePath(imagePath);
        item.setItemType(itemType);
        item.setIsDefault(isDefault);
        item.setUnlockConditionType(conditionType);
        item.setUnlockConditionValue(conditionValue);
        item.setDisplayOrder(displayOrder);
        return item;
    }
}

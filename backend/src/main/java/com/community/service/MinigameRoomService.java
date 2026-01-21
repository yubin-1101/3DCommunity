package com.community.service;

import com.community.dto.MinigamePlayerDto;
import com.community.dto.MinigameRoomDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.community.dto.GameEventDto;
import com.community.dto.GameTargetDto;
import com.community.dto.GameScoreDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class MinigameRoomService {

    private final Map<String, MinigameRoomDto> rooms = new ConcurrentHashMap<>();

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    // Game sessions per room
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Random random = new Random();

    // Reaction round state
    private final Map<String, Boolean> reactionActive = new ConcurrentHashMap<>();
    private final Map<String, String> reactionWinner = new ConcurrentHashMap<>();

    // Omok game state
    private final Map<String, OmokGameSession> omokSessions = new ConcurrentHashMap<>();

    // 한국어기초사전 Open API 설정
    private static final String KRDICT_API_KEY = "F5E1C7AE840AC60C17D459064E04F4E7";
    private static final String KRDICT_API_URL = "https://krdict.korean.go.kr/api/search";

    // 단어 검증 캐시 (API 호출 최소화)
    private static final Set<String> validWordCache = ConcurrentHashMap.newKeySet();
    private static final Set<String> invalidWordCache = ConcurrentHashMap.newKeySet();

    // 한국어 단어 사전 (끝말잇기 검증용 - API 실패시 폴백)
    private static final Set<String> KOREAN_WORDS = new HashSet<>();
    static {
        // 가
        KOREAN_WORDS.addAll(Arrays.asList("가게", "가격", "가구", "가난", "가능", "가득", "가로", "가루", "가방", "가슴", "가요", "가운데", "가을", "가위", "가장", "가정", "가족", "가지", "각각", "각도", "각오", "각자", "간격", "간단", "간부", "간섭", "간신히", "간장", "간접", "간판", "간호", "갈등", "갈비", "갈색", "감각", "감기", "감독", "감동", "감사", "감상", "감소", "감옥", "감자", "감정", "감히", "갑자기", "강", "강도", "강력", "강물", "강사", "강아지", "강의", "강조", "강화", "같이", "개", "개구리", "개미", "개발", "개방", "개선", "개성", "개월", "개인", "거리", "거실", "거울", "거의", "거짓", "건강", "건너", "건물", "건설", "건축", "걸음", "검사", "검정", "게시판", "게임", "겨울", "격차", "견해", "결과", "결국", "결론", "결심", "결정", "결혼", "겸손", "경고", "경기", "경력", "경비", "경쟁", "경제", "경찰", "경치", "경험", "계곡", "계기", "계단", "계란", "계산", "계속", "계약", "계절", "계층", "계획", "고개", "고구마", "고급", "고기", "고려", "고모", "고민", "고생", "고양이", "고유", "고장", "고전", "고집", "고추", "고통", "고향", "곡식", "골목", "골프", "곰", "곱", "공간", "공개", "공격", "공공", "공급", "공기", "공동", "공무원", "공사", "공식", "공연", "공원", "공장", "공주", "공중", "공책", "공통", "공포", "공해", "공휴일", "과거", "과목", "과연", "과일", "과자", "과장", "과정", "과학", "관객", "관계", "관광", "관념", "관련", "관리", "관심", "관점", "관찰", "광고", "광경", "광장", "괴물", "교과서", "교류", "교문", "교사", "교수", "교실", "교양", "교육", "교장", "교통", "교환", "교회", "구경", "구두", "구름", "구멍", "구분", "구석", "구성", "구역", "구입", "구조", "구체", "국가", "국기", "국내", "국민", "국물", "국수", "국어", "국왕", "국적", "국제", "국회", "군대", "군사", "군인", "굴", "굶", "궁금", "권리", "권위", "귀", "귀국", "귀신", "규모", "규정", "규칙", "균형", "귤", "그날", "그네", "그늘", "그때", "그릇", "그림", "그만", "그전", "극복", "극장", "근거", "근교", "근로", "근무", "근본", "근처", "글씨", "글자", "금고", "금년", "금메달", "금방", "금액", "금요일", "금지", "급격", "급식", "급여", "급히", "긍정", "기간", "기계", "기관", "기구", "기능", "기념", "기대", "기도", "기둥", "기록", "기름", "기르다", "기법", "기분", "기사", "기상", "기성", "기숙사", "기술", "기억", "기업", "기온", "기원", "기자", "기적", "기준", "기차", "기초", "기타", "기한", "기호", "기회", "긴장", "길", "김", "김치", "깊이", "꽃", "꽃다발", "꿈", "끝"));
        // 나
        KOREAN_WORDS.addAll(Arrays.asList("나라", "나머지", "나무", "나비", "나이", "나중", "나침반", "낙엽", "난방", "날개", "날씨", "날짜", "남녀", "남동생", "남매", "남방", "남부", "남북", "남산", "남성", "남자", "남쪽", "남편", "남한", "납득", "낭비", "내과", "내년", "내부", "내용", "내일", "냄새", "냉장고", "냉방", "너머", "넓이", "네모", "넥타이", "년도", "노동", "노란색", "노래", "노력", "노선", "노인", "노트", "녹색", "녹음", "녹차", "논리", "논문", "논쟁", "농담", "농민", "농사", "농업", "농촌", "뇌", "누나", "눈", "눈물", "눈썹", "뉴스", "느낌", "능력", "늑대"));
        // 다
        KOREAN_WORDS.addAll(Arrays.asList("다리", "다방", "다수", "다음", "다이어트", "단계", "단골", "단독", "단맛", "단순", "단어", "단위", "단점", "단체", "달", "달걀", "달러", "달력", "달리", "담배", "담요", "담임", "담장", "당근", "당분간", "당시", "당연", "당장", "대개", "대공", "대구", "대기", "대낮", "대담", "대도시", "대략", "대량", "대로", "대리", "대문", "대부분", "대비", "대사", "대상", "대신", "대안", "대응", "대장", "대전", "대중", "대책", "대체", "대충", "대출", "대통령", "대표", "대학", "대학생", "대학원", "대합실", "대형", "대화", "대회", "덕", "덕분", "도구", "도끼", "도대체", "도덕", "도둑", "도로", "도망", "도서관", "도시", "도움", "도자기", "도장", "도전", "도중", "독감", "독립", "독서", "독신", "독일", "독특", "돈", "돌", "동감", "동갑", "동기", "동네", "동료", "동물", "동생", "동서", "동시", "동아리", "동양", "동의", "동작", "동전", "동창", "동쪽", "동포", "동화", "돼지", "된장", "두부", "두통", "둘째", "뒤쪽", "드라마", "등", "등록", "등불", "등산", "등장", "땀", "땅", "때", "때문", "떡", "떡볶이", "또래"));
        // 라
        KOREAN_WORDS.addAll(Arrays.asList("라디오", "라면", "라운드", "라이벌", "라이터", "랭킹", "러시아", "레몬", "레스토랑", "레저", "레코드", "로봇", "로비", "록", "롤러", "리듬", "리본", "리조트", "리터"));
        // 마
        KOREAN_WORDS.addAll(Arrays.asList("마늘", "마당", "마라톤", "마련", "마루", "마무리", "마술", "마약", "마음", "마을", "마이크", "마지막", "마찬가지", "마찰", "마크", "마트", "막내", "만남", "만두", "만세", "만약", "만일", "만족", "만화", "말", "말씀", "맘", "맛", "맞이", "매년", "매달", "매력", "매일", "매장", "매체", "맥주", "머리", "머리카락", "먹이", "먼지", "멀리", "멍", "메뉴", "메달", "메모", "메시지", "멜로디", "며칠", "면", "면적", "명령", "명단", "명문", "명성", "명예", "명의", "명절", "명함", "모기", "모델", "모든", "모래", "모레", "모습", "모양", "모으다", "모음", "모자", "모집", "모퉁이", "목", "목걸이", "목록", "목사", "목소리", "목숨", "목요일", "목욕", "목적", "목표", "몫", "몸", "몸무게", "몸짓", "못", "무게", "무관심", "무궁화", "무기", "무늬", "무대", "무덤", "무렵", "무료", "무리", "무릎", "무사", "무역", "무용", "무지개", "무척", "묵", "문", "문구", "문법", "문서", "문의", "문자", "문장", "문제", "문학", "문화", "물가", "물건", "물결", "물고기", "물론", "물속", "물질", "물체", "뮤지컬", "미국", "미끄럼", "미녀", "미래", "미루다", "미리", "미사일", "미소", "미술", "미술관", "미안", "미역", "미용실", "미움", "미인", "미터", "미팅", "미혼", "민간", "민속", "민요", "민족", "민주", "밀가루", "밑"));
        // 바
        KOREAN_WORDS.addAll(Arrays.asList("바가지", "바구니", "바깥", "바나나", "바늘", "바다", "바닥", "바람", "바보", "바위", "바이러스", "바이올린", "바탕", "박물관", "박사", "박수", "반", "반대", "반드시", "반려", "반면", "반복", "반성", "반응", "반장", "반찬", "반쪽", "받침", "발", "발견", "발달", "발레", "발목", "발생", "발음", "발자국", "발전", "발표", "발휘", "밤", "밥", "밥그릇", "밥상", "방", "방금", "방면", "방문", "방법", "방송", "방식", "방안", "방언", "방울", "방위", "방지", "방침", "방학", "방해", "방향", "밭", "배", "배경", "배구", "배꼽", "배달", "배드민턴", "배려", "배우", "배추", "배치", "백", "백두산", "백색", "백성", "백인", "백화점", "버릇", "버섯", "버스", "버튼", "번개", "번역", "번호", "벌", "벌금", "벌레", "벌써", "범위", "범인", "범죄", "법", "법률", "법원", "법적", "법칙", "벗", "벚꽃", "베개", "벤치", "벨트", "변경", "변동", "변명", "변신", "변화", "별", "별도", "별로", "별명", "병", "병실", "병원", "보관", "보너스", "보도", "보람", "보리", "보물", "보살핌", "보상", "보수", "보안", "보완", "보육", "보이차", "보장", "보조", "보존", "보증", "보충", "보통", "보트", "보편", "보험", "복", "복도", "복사", "복습", "복잡", "복지", "볶음", "본래", "본문", "본부", "본사", "본인", "본질", "볼", "볼펜", "봄", "봉사", "봉지", "봉투", "부근", "부담", "부동산", "부드럽다", "부딪치다", "부러움", "부르다", "부모", "부문", "부분", "부부", "부분", "부상", "부엌", "부위", "부인", "부자", "부작용", "부장", "부정", "부족", "부주의", "부지런히", "부채", "부탁", "부품", "부피", "북", "북쪽", "북한", "분노", "분량", "분류", "분리", "분명", "분석", "분수", "분야", "분위기", "분쟁", "분포", "분필", "분홍색", "불", "불가능", "불꽃", "불만", "불법", "불빛", "불안", "불이익", "불편", "불평", "불행", "붉은색", "붓", "비", "비교", "비극", "비난", "비닐", "비누", "비둘기", "비디오", "비만", "비명", "비밀", "비바람", "비상", "비서", "비용", "비율", "비중", "비타민", "비판", "비행", "비행기", "빈곤", "빌딩", "빗", "빗물", "빛", "빠짐", "빨래", "뼈"));
        // 사
        KOREAN_WORDS.addAll(Arrays.asList("사건", "사계절", "사고", "사과", "사교", "사귀다", "사냥", "사다리", "사람", "사랑", "사례", "사립", "사망", "사모님", "사무실", "사물", "사방", "사상", "사생활", "사설", "사슴", "사실", "사업", "사용", "사원", "사월", "사이", "사이트", "사자", "사장", "사전", "사정", "사진", "사촌", "사춘기", "사치", "사탕", "사투리", "사파리", "사표", "사학", "사회", "사흘", "산", "산길", "산꼭대기", "산림", "산불", "산소", "산업", "산책", "살", "살림", "삶", "삼계탕", "삼국", "삼촌", "상가", "상관", "상금", "상담", "상당", "상대", "상류", "상반기", "상상", "상식", "상업", "상위", "상인", "상자", "상점", "상징", "상처", "상추", "상태", "상품", "상황", "새", "새벽", "새해", "색", "색깔", "샌드위치", "생각", "생명", "생물", "생산", "생선", "생신", "생일", "생활", "샤워", "서랍", "서류", "서로", "서론", "서명", "서민", "서비스", "서양", "서울", "서적", "서점", "서쪽", "서클", "석사", "석유", "석탄", "섞다", "선거", "선물", "선배", "선발", "선생님", "선수", "선원", "선장", "선전", "선진국", "선택", "선풍기", "설거지", "설날", "설득", "설렁탕", "설명", "설문", "설비", "설사", "설악산", "설탕", "섬", "섬유", "성", "성격", "성경", "성공", "성당", "성명", "성별", "성인", "성장", "성적", "성질", "성함", "성향", "세계", "세금", "세기", "세대", "세련", "세로", "세상", "세수", "세월", "세탁기", "세탁소", "센터", "센티미터", "셀프", "셋째", "소개", "소금", "소극적", "소나기", "소나무", "소년", "소녀", "소득", "소망", "소매", "소문", "소비", "소설", "소수", "소식", "소용", "소원", "소유", "소음", "소중", "소질", "소파", "소포", "소풍", "소화", "속", "속담", "속도", "속상", "속옷", "손", "손가락", "손길", "손님", "손등", "손바닥", "손뼉", "손실", "손잡이", "손해", "솔직", "솜씨", "송아지", "송이", "송편", "쇠고기", "쇼핑", "수건", "수년", "수단", "수도", "수렵", "수리", "수많다", "수면", "수명", "수박", "수상", "수석", "수술", "수업", "수영", "수요", "수요일", "수입", "수준", "수집", "수출", "수필", "수학", "수험생", "숙녀", "숙박", "숙소", "숙제", "순간", "순서", "순수", "순위", "술", "술집", "숫자", "숲", "쉼", "쉼표", "스님", "스승", "스스로", "스웨터", "스위치", "스카프", "스키", "스타", "스타일", "스테이크", "스트레스", "스포츠", "스프", "슬픔", "습관", "습기", "승객", "승리", "승부", "승용차", "승진", "시", "시각", "시간", "시골", "시급", "시기", "시끄럽다", "시내", "시대", "시댁", "시들다", "시리즈", "시멘트", "시민", "시범", "시부모", "시선", "시설", "시스템", "시아버지", "시어머니", "시월", "시위", "시인", "시일", "시작", "시장", "시절", "시점", "시청", "시키다", "시험", "식", "식구", "식량", "식료품", "식물", "식빵", "식사", "식용유", "식욕", "식초", "식탁", "식품", "식히다", "신고", "신규", "신기", "신념", "신문", "신발", "신분", "신비", "신사", "신선", "신세", "신앙", "신용", "신인", "신입", "신제품", "신청", "신체", "신호", "신혼부부", "실감", "실내", "실례", "실력", "실리", "실망", "실수", "실습", "실시", "실업", "실정", "실제", "실천", "실컷", "실태", "실패", "실험", "실현", "심각", "심리", "심부름", "심사", "심장", "심판", "심하다", "십대", "십자가", "싸움", "쌀", "쌍둥이", "썰매", "쓰기", "쓰레기", "쓸모"));
        // 아
        KOREAN_WORDS.addAll(Arrays.asList("아가씨", "아기", "아까", "아끼다", "아내", "아들", "아래", "아래층", "아름답다", "아르바이트", "아무리", "아버지", "아빠", "아시아", "아예", "아울러", "아이", "아이디어", "아저씨", "아줌마", "아직", "아침", "아파트", "아프리카", "아픔", "아홉", "악기", "악몽", "악수", "악화", "안개", "안경", "안과", "안내", "안녕", "안다", "안락", "안방", "안부", "안심", "안전", "안정", "안쪽", "안팎", "알", "알맹이", "알코올", "암", "암컷", "암탉", "압력", "앞", "앞길", "앞뒤", "앞문", "앞서다", "앞쪽", "애완동물", "애인", "애정", "액수", "앨범", "야간", "야구", "야단", "야외", "야채", "약", "약간", "약국", "약사", "약속", "약점", "약품", "약혼녀", "양", "양념", "양말", "양배추", "양복", "양식", "양옆", "양주", "양파", "얕다", "얘기", "어깨", "어느새", "어둠", "어디", "어려움", "어른", "어린이", "어머니", "어법", "어젯밤", "어쨌든", "어찌", "억", "억울", "언니", "언덕", "언론", "언어", "얼굴", "얼마", "얼음", "엄마", "엄청나다", "업무", "업종", "업체", "엉덩이", "에너지", "에어컨", "여가", "여관", "여군", "여기", "여기저기", "여대생", "여동생", "여든", "여러", "여론", "여름", "여부", "여성", "여우", "여유", "여인", "여자", "여전히", "여행", "역", "역사", "역시", "역할", "연결", "연구", "연극", "연기", "연락", "연령", "연말", "연상", "연설", "연세", "연속", "연습", "연애", "연예인", "연인", "연장", "연주", "연출", "연필", "연합", "연휴", "열", "열기", "열량", "열매", "열쇠", "열심히", "열차", "염려", "엽서", "영", "영광", "영국", "영상", "영양", "영어", "영역", "영웅", "영원", "영향", "영화", "영혼", "옆", "옆구리", "옆방", "옆집", "예금", "예매", "예문", "예민", "예방", "예보", "예비", "예산", "예상", "예선", "예술", "예식장", "예약", "예외", "예의", "예절", "예정", "예컨대", "예측", "옛날", "오늘", "오락", "오래", "오렌지", "오른쪽", "오리", "오염", "오월", "오이", "오전", "오징어", "오페라", "오피스텔", "오해", "오히려", "오후", "옥상", "옥수수", "온갖", "온도", "온몸", "온종일", "온통", "올라가다", "올림픽", "올해", "옷", "옷감", "옷장", "옷차림", "와인", "완벽", "완성", "완전", "왕", "왕비", "왕자", "왜냐하면", "외갓집", "외국", "외교", "외로움", "외모", "외부", "외삼촌", "외숙모", "외출", "외할머니", "외할아버지", "외환", "왼발", "왼손", "왼쪽", "요금", "요구", "요리", "요새", "요소", "요약", "요일", "요즘", "요청", "욕", "욕실", "욕심", "용감", "용기", "용돈", "용도", "용서", "용어", "용품", "우동", "우리", "우산", "우선", "우승", "우연히", "우유", "우울", "우유", "우정", "우체국", "운", "운동", "운동장", "운동화", "운명", "운반", "운전", "운행", "울음", "웃음", "원", "원고", "원래", "원리", "원서", "원숭이", "원인", "원칙", "원피스", "월", "월급", "월드컵", "월요일", "웬만하다", "위", "위기", "위로", "위반", "위생", "위성", "위원", "위치", "위험", "윗사람", "유난히", "유능", "유독", "유럽", "유리", "유머", "유명", "유물", "유사", "유산", "유월", "유의", "유적", "유전", "유지", "유치원", "유학", "유해", "유행", "유형", "육군", "육상", "육십", "육체", "윤리", "은", "은메달", "은행", "음", "음료", "음료수", "음반", "음식", "음식점", "음악", "음주", "응답", "의견", "의논", "의도", "의문", "의미", "의복", "의사", "의식", "의심", "의외", "의욕", "의원", "의자", "의지", "의학", "이", "이것", "이곳", "이기다", "이날", "이념", "이놈", "이달", "이동", "이때", "이래", "이력서", "이론", "이루다", "이름", "이마", "이모", "이미", "이민", "이발소", "이번", "이불", "이빨", "이사", "이상", "이성", "이슬", "이야기", "이어지다", "이유", "이웃", "이월", "이익", "이전", "이제", "이중", "이직", "이쪽", "이틀", "이해", "이혼", "익숙하다", "인간", "인격", "인공", "인구", "인기", "인류", "인물", "인분", "인사", "인삼", "인상", "인생", "인식", "인연", "인원", "인재", "인정", "인종", "인천", "인체", "인터넷", "인터뷰", "인하", "인형", "일", "일곱", "일기", "일단", "일대", "일등", "일반", "일부", "일부러", "일상", "일생", "일손", "일시", "일요일", "일월", "일으키다", "일자리", "일정", "일종", "일주일", "일찍", "일치", "일행", "일회용", "임금", "임무", "임산부", "임시", "임신", "입", "입구", "입금", "입력", "입맛", "입사", "입술", "입시", "입원", "입장", "입학", "잇몸", "잊다", "자격", "자극", "자기", "자꾸", "자녀", "자동", "자동차", "자랑", "자료", "자리", "자막", "자매", "자명종", "자문", "자살", "자세", "자신", "자연", "자연스럽다", "자원", "자유", "자전거", "자정", "자존심", "자주", "자체", "자판", "작가", "작년", "작다", "작동", "작문", "작성", "작업", "작용", "작은아버지", "작은어머니", "작품", "잔", "잔디", "잔치", "잠", "잠깐", "잠수함", "잠시", "잠옷", "잡다", "잡지", "장관", "장기간", "장난", "장남", "장녀", "장례", "장르", "장마", "장면", "장모", "장모님", "장미", "장비", "장사", "장소", "장애인", "장인", "장점", "장학금", "재능", "재다", "재료", "재미", "재산", "재생", "재정", "재주", "재채기", "재판", "재학", "쟁반", "저", "저거", "저고리", "저곳", "저기", "저금", "저녁", "저런", "저렇게", "저번", "저울", "저자", "저절로", "저쪽", "저희", "적", "적극", "적당", "적성", "적용", "적응", "적절", "적히다", "전개", "전공", "전구", "전국", "전기", "전날", "전달", "전라도", "전망", "전문", "전반", "전부", "전선", "전설", "전세", "전시", "전시회", "전원", "전자", "전쟁", "전제", "전주", "전체", "전통", "전혀", "전화", "전화기", "전화번호", "전후", "절", "절대", "절반", "절약", "절차", "젊음", "점", "점심", "점원", "점점", "점수", "점차", "접근", "접다", "접시", "접촉", "젓가락", "정거장", "정답", "정도", "정리", "정문", "정부", "정비", "정상", "정성", "정신", "정오", "정원", "정육점", "정의", "정장", "정전", "정직", "정치", "정확", "젖", "젖히다", "제공", "제과점", "제국", "제대로", "제도", "제목", "제발", "제법", "제비", "제사", "제시", "제안", "제외", "제일", "제자", "제작", "제주도", "제출", "제품", "제한", "조각", "조건", "조금", "조깅", "조리", "조명", "조미료", "조상", "조선", "조심", "조절", "조정", "조직", "조카", "족", "존경", "존대", "존재", "졸업", "졸음", "좀", "좁히다", "종교", "종류", "종소리", "종업원", "종이", "종일", "종종", "좌석", "좌우", "죄", "죄송", "주", "주거", "주관", "주로", "주류", "주먹", "주문", "주민", "주방", "주변", "주부", "주사", "주소", "주식", "주요", "주의", "주인", "주일", "주장", "주전자", "주제", "주차", "주택", "주한", "주황색", "죽음", "준비", "줄", "줄기", "줄무늬", "줌", "중간", "중계방송", "중국", "중년", "중단", "중독", "중반", "중부", "중세", "중소기업", "중순", "중심", "중앙", "중얼거리다", "중요", "중학교", "쥐", "즉석", "즐거움", "증가", "증거", "증권", "증상", "증세", "증시", "지각", "지갑", "지경", "지구", "지금", "지급", "지나가다", "지난달", "지난번", "지난주", "지난해", "지능", "지다", "지도", "지도자", "지루하다", "지름길", "지리", "지방", "지붕", "지불", "지식", "지역", "지우개", "지원", "지위", "지점", "지진", "지출", "지키다", "지하", "지하도", "지하철", "지혜", "직선", "직업", "직원", "직장", "직전", "직접", "직후", "진급", "진단", "진동", "진료", "진리", "진심", "진실", "진짜", "진출", "진통", "진행", "질", "질문", "질병", "질서", "질투", "짐", "집", "집단", "집안", "집중", "짓", "짓다", "짙다", "짝", "짝꿍", "짬뽕", "쪽", "찌개", "찍다"));
        // 차
        KOREAN_WORDS.addAll(Arrays.asList("차", "차갑다", "차량", "차례", "차마", "차선", "차이", "차창", "차츰", "착각", "찬물", "찬성", "찬스", "참가", "참고", "참기름", "참다", "참새", "참석", "참외", "참여", "참조", "창가", "창고", "창구", "창문", "창조", "창피", "채널", "채소", "채우다", "책", "책가방", "책방", "책상", "책임", "챔피언", "처리", "처벌", "처음", "천", "천국", "천둥", "천만", "천장", "천재", "천천히", "철", "철도", "철저", "첫날", "첫째", "청년", "청바지", "청소", "청소기", "청소년", "청춘", "체계", "체력", "체온", "체육", "체조", "체중", "체험", "초", "초기", "초대", "초등학교", "초록색", "초밥", "초보", "초상화", "초점", "초콜릿", "촌", "촌스럽다", "총", "총각", "총리", "총장", "촬영", "최고", "최근", "최대", "최선", "최소", "최신", "최악", "최우선", "최종", "최초", "최후", "추가", "추억", "추위", "추진", "추측", "추천", "축구", "축소", "축제", "축하", "출구", "출근", "출발", "출산", "출석", "출신", "출입", "출장", "출판", "출현", "충격", "충고", "충돌", "충분", "취미", "취소", "취업", "취직", "취하다", "측면", "치과", "치다", "치료", "치마", "치아", "치약", "치즈", "친구", "친밀", "친정", "친척", "친하다", "칠", "칠월", "칠판", "침", "침대", "침묵", "침실", "칫솔", "칭찬"));
        // 카
        KOREAN_WORDS.addAll(Arrays.asList("카드", "카레", "카메라", "카운터", "카페", "칼", "칼국수", "캐릭터", "캐리어", "캠페인", "캠퍼스", "커튼", "커피", "컴퓨터", "컵", "케이크", "코", "코끼리", "코너", "코드", "코스", "코스모스", "코트", "코피", "콘서트", "콘텐츠", "콧물", "콩", "콩나물", "쾌감", "쿠폰", "크기", "크리스마스", "클래식", "클럽", "키", "키로", "킬로그램", "킬로미터"));
        // 타
        KOREAN_WORDS.addAll(Arrays.asList("타다", "타입", "탁구", "탁자", "탄생", "탈락", "탈출", "탐구", "탑", "탓", "태권도", "태도", "태양", "태풍", "택배", "택시", "터널", "터미널", "턱", "털", "테니스", "테러", "테스트", "테이블", "텔레비전", "토끼", "토론", "토마토", "토요일", "톤", "통", "통계", "통과", "통로", "통신", "통역", "통일", "통장", "통제", "통증", "통지", "통화", "퇴근", "투자", "투표", "튀김", "트럭", "튼튼", "특기", "특별", "특성", "특수", "특이", "특정", "특징", "특히", "틀림", "틈", "티셔츠", "팀"));
        // 파
        KOREAN_WORDS.addAll(Arrays.asList("파괴", "파란색", "파리", "파악", "파일", "파출소", "파티", "판", "판결", "판단", "판매", "판사", "팔", "팔다", "팔월", "팝송", "패션", "팩스", "팬", "팬티", "퍼센트", "페이지", "펜", "편", "편견", "편도", "편리", "편안", "편의", "편지", "편하다", "평가", "평균", "평등", "평범", "평생", "평소", "평야", "평일", "평화", "폐지", "포도", "포도주", "포스터", "포인트", "포장", "포크", "포함", "폭력", "폭발", "폭우", "표", "표면", "표시", "표정", "표준", "표현", "푸르다", "풀", "품목", "품질", "풍경", "풍부", "풍선", "풍속", "풍습", "프로", "프로그램", "플라스틱", "피", "피곤", "피다", "피땀", "피로", "피부", "피아노", "피우다", "피자", "피해", "필기", "필름", "필수", "필요", "필자", "필통"));
        // 하
        KOREAN_WORDS.addAll(Arrays.asList("하늘", "하느님", "하다", "하드웨어", "하반기", "하숙집", "하순", "하얀색", "하여튼", "하인", "하필", "학과", "학교", "학급", "학기", "학년", "학력", "학번", "학부모", "학비", "학생", "학습", "학용품", "학원", "학자", "학점", "한가운데", "한강", "한계", "한국", "한국어", "한국인", "한글", "한꺼번에", "한눈", "한동안", "한두", "한라산", "한마디", "한복", "한순간", "한식", "한심", "한약", "한옥", "한자", "한정", "한참", "한창", "한편", "할머니", "할아버지", "할인", "함께", "합격", "합리", "합의", "항공", "항구", "항상", "항의", "해", "해결", "해군", "해당", "해롭다", "해산물", "해석", "해소", "해수욕장", "해안", "해외", "해일", "핵심", "핸드백", "햄버거", "햇볕", "햇살", "행동", "행복", "행사", "행위", "행정", "향", "향기", "향상", "향수", "향하다", "허락", "허리", "허용", "헌법", "험하다", "헤어지다", "헬기", "현관", "현금", "현대", "현상", "현실", "현장", "현재", "현지", "혈액", "협력", "협회", "형", "형님", "형부", "형사", "형성", "형수", "형식", "형제", "형태", "형편", "혜택", "호", "호기심", "호남", "호랑이", "호박", "호수", "호실", "호텔", "호흡", "혹시", "혼나다", "혼란", "혼자", "홈페이지", "홍보", "홍수", "홍차", "화", "화가", "화나다", "화난", "화려", "화면", "화분", "화살", "화상", "화요일", "화원", "화이팅", "화재", "화장", "화장실", "화장품", "화폐", "화학", "확대", "확보", "확산", "확실", "확신", "확인", "확장", "환경", "환영", "환율", "환자", "활기", "활동", "활발", "활용", "황금", "회", "회계", "회관", "회복", "회비", "회사", "회색", "회원", "회의", "회장", "회전", "회화", "횟수", "효과", "효도", "효율", "후기", "후반", "후보", "후배", "후추", "후회", "훈련", "훨씬", "휴가", "휴게실", "휴대", "휴식", "휴일", "휴지", "흉내", "흐름", "흑백", "흑인", "흔적", "흔히", "흙", "흡수", "흥미", "흥분", "희곡", "희망", "희생", "흰색", "힘"));
    }

    /**
     * 한국어기초사전 API를 사용하여 단어 유효성 검증
     * 캐시를 사용하여 API 호출 최소화
     */
    private boolean isValidKoreanWord(String word) {
        if (word == null || word.trim().isEmpty()) {
            return false;
        }

        String trimmedWord = word.trim();

        // 1. 유효 단어 캐시 확인
        if (validWordCache.contains(trimmedWord)) {
            return true;
        }

        // 2. 무효 단어 캐시 확인
        if (invalidWordCache.contains(trimmedWord)) {
            return false;
        }

        // 3. 로컬 사전 확인 (빠른 응답)
        if (KOREAN_WORDS.contains(trimmedWord)) {
            validWordCache.add(trimmedWord);
            return true;
        }

        // 4. API 호출하여 검증
        try {
            boolean isValid = checkWordWithKrdictAPI(trimmedWord);
            if (isValid) {
                validWordCache.add(trimmedWord);
                log.info("단어 '{}' API 검증 성공", trimmedWord);
            } else {
                invalidWordCache.add(trimmedWord);
                log.info("단어 '{}' API 검증 실패 - 사전에 없음", trimmedWord);
            }
            return isValid;
        } catch (Exception e) {
            log.warn("API 호출 실패, 로컬 사전으로 폴백: {}", e.getMessage());
            // API 실패시 로컬 사전으로 폴백
            return KOREAN_WORDS.contains(trimmedWord);
        }
    }

    /**
     * 한국어기초사전 API 호출
     */
    private boolean checkWordWithKrdictAPI(String word) throws Exception {
        String encodedWord = URLEncoder.encode(word, StandardCharsets.UTF_8.toString());
        String apiUrl = KRDICT_API_URL + "?key=" + KRDICT_API_KEY + "&q=" + encodedWord + "&part=word&sort=dict";

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(3000); // 3초 타임아웃
        conn.setReadTimeout(3000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("API 응답 오류: " + responseCode);
        }

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        String xmlResponse = response.toString();

        // XML에서 total 값 파싱 (검색 결과 수)
        // <total>0</total> 이면 단어 없음
        // <total>1</total> 이상이면 단어 있음
        if (xmlResponse.contains("<total>0</total>")) {
            return false;
        }

        // 검색 결과가 있으면 정확한 단어 매칭 확인
        // <word>단어</word> 태그에서 정확히 일치하는지 확인
        String wordPattern = "<word>" + word + "</word>";
        if (xmlResponse.contains(wordPattern)) {
            return true;
        }

        // 부분 매칭도 허용 (API가 유사 단어도 반환하므로)
        // total이 0이 아니고 검색어가 포함되어 있으면 유효
        return xmlResponse.contains("<word>") && !xmlResponse.contains("<total>0</total>");
    }

    /**
     * 방 생성
     */
    public MinigameRoomDto createRoom(String roomName, String gameName, String hostId, String hostName,
            int maxPlayers, boolean isLocked, int hostLevel,
            String selectedProfile, String selectedOutline, Double gpsLng, Double gpsLat) {
        String roomId = UUID.randomUUID().toString();

        MinigameRoomDto room = new MinigameRoomDto();
        room.setRoomId(roomId);
        room.setRoomName(roomName);
        room.setGameName(gameName);
        room.setHostId(hostId);
        room.setHostName(hostName);
        room.setMaxPlayers(maxPlayers);
        room.setLocked(isLocked);
        room.setPlaying(false);
        room.setCurrentPlayers(1);
        room.setGpsLng(gpsLng);
        room.setGpsLat(gpsLat);

        MinigamePlayerDto host = new MinigamePlayerDto();
        host.setUserId(hostId);
        host.setUsername(hostName);
        host.setLevel(hostLevel);
        host.setHost(true);
        host.setReady(true);
        host.setSelectedProfile(selectedProfile);
        host.setSelectedOutline(selectedOutline);

        room.getPlayers().add(host);
        rooms.put(roomId, room);

        log.info("방 생성: {} (ID: {}, GPS: {}, {})", roomName, roomId, gpsLng, gpsLat);
        return room;
    }

    /**
     * 방 목록 조회
     */
    public List<MinigameRoomDto> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * 방 조회
     */
    public MinigameRoomDto getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * 방 입장 (참가자 또는 관전자로)
     */
    public MinigameRoomDto joinRoom(String roomId, MinigamePlayerDto player) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            log.error("방을 찾을 수 없습니다: {}", roomId);
            return null;
        }

        // 이미 플레이어로 있는지 확인
        boolean alreadyPlayer = room.getPlayers().stream().anyMatch(p -> p.getUserId().equals(player.getUserId()));
        // 이미 관전자로 있는지 확인
        boolean alreadySpectator = room.getSpectators().stream().anyMatch(p -> p.getUserId().equals(player.getUserId()));

        if (alreadyPlayer || alreadySpectator) {
            log.info("플레이어 {}는 이미 방에 있습니다: {}", player.getUsername(), roomId);
            return room;
        }

        log.info("방 상태 before join - roomId: {}, currentPlayers: {}, maxPlayers: {}, players: {}",
                roomId, room.getCurrentPlayers(), room.getMaxPlayers(),
                room.getPlayers().stream().map(p -> p.getUserId()).toList());

        // 참가 인원이 가득 찬 경우 관전자로 입장
        if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
            room.getSpectators().add(player);
            log.info("플레이어 {} 관전자로 입장: {} (관전자 수: {})",
                    player.getUsername(), roomId, room.getSpectators().size());
        } else {
            // 참가자로 입장
            room.getPlayers().add(player);
            room.setCurrentPlayers(room.getCurrentPlayers() + 1);
            log.info("플레이어 {} 참가자로 입장: {} (현재 {}/{})",
                    player.getUsername(), roomId, room.getCurrentPlayers(), room.getMaxPlayers());
        }

        return room;
    }

    /**
     * 방 나가기
     */
    public MinigameRoomDto leaveRoom(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        // 참가자 목록에서 제거
        boolean wasPlayer = room.getPlayers().removeIf(p -> p.getUserId().equals(userId));
        // 관전자 목록에서 제거
        boolean wasSpectator = room.getSpectators().removeIf(p -> p.getUserId().equals(userId));

        if (wasPlayer) {
            room.setCurrentPlayers(room.getPlayers().size());
            log.info("참가자 {} 방 나가기: {} (현재 {}/{})", userId, roomId, room.getCurrentPlayers(), room.getMaxPlayers());

            // 게임 중에 참가자가 나가서 인원이 부족한 경우
            if (room.isPlaying() && "오목".equals(room.getGameName()) && room.getPlayers().size() < 2) {
                log.info("오목 게임 중 인원 부족으로 게임 종료: roomId={}", roomId);
                room.setPlaying(false);

                // 모든 플레이어의 준비 상태 초기화
                for (MinigamePlayerDto player : room.getPlayers()) {
                    if (!player.isHost()) {
                        player.setReady(false);
                    }
                }

                // 오목 타이머 중지
                OmokGameSession omokSession = omokSessions.remove(roomId);
                if (omokSession != null && omokSession.timerFuture != null) {
                    omokSession.timerFuture.cancel(false);
                }

                // 게임 종료 이벤트 브로드캐스트
                GameEventDto gameEndEvt = new GameEventDto();
                gameEndEvt.setRoomId(roomId);
                gameEndEvt.setType("gameEndByPlayerLeave");
                gameEndEvt.setPayload("insufficient_players");
                gameEndEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", gameEndEvt);
                log.info("게임 종료 이벤트 전송: roomId={}, type=gameEndByPlayerLeave", roomId);

                // 방 상태 업데이트 브로드캐스트
                room.setAction("gameEndByPlayerLeave");
                room.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId, room);
                log.info("방 업데이트 전송: roomId={}, action=gameEndByPlayerLeave, playing={}", roomId, room.isPlaying());
            }
        } else if (wasSpectator) {
            log.info("관전자 {} 방 나가기: {} (관전자 수: {})", userId, roomId, room.getSpectators().size());
        }

        // 방장이 나갔을 때
        if (room.getHostId().equals(userId)) {
            if (room.getPlayers().isEmpty()) {
                // 방 삭제
                rooms.remove(roomId);
                log.info("방 삭제: {}", roomId);
                return null;
            } else {
                // 다음 사람을 방장으로 지정
                MinigamePlayerDto newHost = room.getPlayers().get(0);
                newHost.setHost(true);
                room.setHostId(newHost.getUserId());
                room.setHostName(newHost.getUsername());
                log.info("새로운 방장: {}", newHost.getUsername());
            }
        }

        return room;
    }

    /**
     * 방 설정 변경
     */
    public MinigameRoomDto updateRoomSettings(String roomId, String gameName, int maxPlayers) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setGameName(gameName);

        // 최대 인원 수를 줄였을 때 초과 인원을 관전자로 이동
        if (maxPlayers < room.getMaxPlayers()) {
            int currentPlayers = room.getPlayers().size();
            if (currentPlayers > maxPlayers) {
                // 초과된 플레이어 수 계산
                int excessPlayers = currentPlayers - maxPlayers;

                // 뒤에서부터 플레이어를 관전자로 이동 (방장 제외)
                List<MinigamePlayerDto> playersToMove = new ArrayList<>();
                for (int i = room.getPlayers().size() - 1; i >= 0 && playersToMove.size() < excessPlayers; i--) {
                    MinigamePlayerDto player = room.getPlayers().get(i);
                    // 방장이 아닌 경우에만 이동
                    if (!player.isHost()) {
                        playersToMove.add(player);
                    }
                }

                // 관전자로 이동
                for (MinigamePlayerDto player : playersToMove) {
                    room.getPlayers().remove(player);
                    player.setReady(false); // 준비 상태 초기화
                    room.getSpectators().add(player);
                    log.info("플레이어 {}를 관전자로 이동: roomId={}", player.getUsername(), roomId);
                }

                // 현재 플레이어 수 업데이트
                room.setCurrentPlayers(room.getPlayers().size());
            }
        }

        room.setMaxPlayers(maxPlayers);

        log.info("방 설정 변경: {} - 게임: {}, 최대 인원: {}", roomId, gameName, maxPlayers);
        return room;
    }

    /**
     * 준비 상태 변경
     */
    public MinigameRoomDto toggleReady(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .ifPresent(p -> p.setReady(!p.isReady()));

        return room;
    }

    /**
     * 참가자 <-> 관전자 역할 전환
     */
    public MinigameRoomDto switchRole(String roomId, String userId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            log.warn("방을 찾을 수 없음: {}", roomId);
            return null;
        }

        // 현재 참가자인지 확인
        MinigamePlayerDto player = room.getPlayers().stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (player != null) {
            // 참가자 -> 관전자
            boolean isHost = player.isHost();

            room.getPlayers().remove(player);
            room.setCurrentPlayers(room.getPlayers().size());

            // 관전자 리스트에 추가 (ready 상태 초기화, 방장 상태는 유지)
            player.setReady(false);
            // 방장인 경우 방장 상태 유지 (host 필드 그대로 유지)
            room.getSpectators().add(player);

            log.info("참가자 -> 관전자: userId={}, roomId={}, isHost={}", userId, roomId, isHost);
        } else {
            // 관전자인지 확인
            MinigamePlayerDto spectator = room.getSpectators().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .findFirst()
                    .orElse(null);

            if (spectator != null) {
                // 관전자 -> 참가자
                // 방이 가득 찬 경우 전환 불가
                if (room.getCurrentPlayers() >= room.getMaxPlayers()) {
                    log.warn("방이 가득 참. 참가자로 전환 불가: userId={}, roomId={}", userId, roomId);
                    return null;
                }

                room.getSpectators().remove(spectator);
                room.getPlayers().add(spectator);
                room.setCurrentPlayers(room.getPlayers().size());

                log.info("관전자 -> 참가자: userId={}, roomId={}", userId, roomId);
            } else {
                log.warn("유저를 찾을 수 없음: userId={}, roomId={}", userId, roomId);
                return null;
            }
        }

        return room;
    }

    // Inner class to hold session state
    private static class GameSession {
        private final String roomId;
        Map<String, GameTargetDto> activeTargets = new ConcurrentHashMap<>();
        Map<String, Integer> scores = new ConcurrentHashMap<>();
        java.util.concurrent.ScheduledFuture<?> future;
        private int remainingSeconds = 0;

        public GameSession(String roomId) {
            this.roomId = roomId;
        }

        public int getRemainingSeconds() {
            return remainingSeconds;
        }

        public void setRemainingSeconds(int s) {
            remainingSeconds = s;
        }

        public void decrementRemainingSeconds() {
            remainingSeconds--;
        }
    }

    /**
     * 게임 시작
     */
    // Aiming Game Constants
    private static final int GAME_DURATION = 30; // 30초 게임
    private static final int MAX_TARGETS = 3; // 최대 3개 타겟 동시 표시
    private static final int TARGET_SPAWN_INTERVAL = 2; // 2초마다 타겟 생성 체크

    /**
     * 게임 시작
     */
    public MinigameRoomDto startGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setPlaying(true);
        log.info("게임 시작: {}", roomId);

        // Initialize game session
        GameSession session = new GameSession(roomId);
        sessions.put(roomId, session);

        // Broadcast gameStart event
        GameEventDto startEvent = new GameEventDto();
        startEvent.setRoomId(roomId);
        startEvent.setType("gameStart");
        startEvent.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", startEvent);

        // 에임 맞추기 게임 로직
        if ("에임 맞추기".equals(room.getGameName())) {
            session.setRemainingSeconds(GAME_DURATION);

            // 초기 타겟 생성 (최대 3개)
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    for (int i = 0; i < MAX_TARGETS; i++) {
                        spawnTarget(roomId);
                        Thread.sleep(200); // 타겟 간 약간의 간격
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            // 주기적으로 타겟 생성 및 타이머 업데이트
            session.future = scheduler.scheduleAtFixedRate(() -> {
                try {
                    GameSession s = sessions.get(roomId);
                    if (s == null) return;

                    // 타이머 감소
                    s.decrementRemainingSeconds();

                    // 타이머 업데이트 브로드캐스트
                    GameEventDto timerEvt = new GameEventDto();
                    timerEvt.setRoomId(roomId);
                    timerEvt.setType("aimTimer");
                    timerEvt.setPayload(String.valueOf(s.getRemainingSeconds()));
                    timerEvt.setTimestamp(System.currentTimeMillis());
                    messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                    // 타겟 생성 (최대 3개까지)
                    if (s.getRemainingSeconds() % TARGET_SPAWN_INTERVAL == 0) {
                        spawnTarget(roomId);
                    }

                    // 시간 종료 시 게임 종료
                    if (s.getRemainingSeconds() <= 0) {
                        if (s.future != null) {
                            s.future.cancel(false);
                        }
                        endGameSession(roomId);
                    }
                } catch (Exception e) {
                    log.error("에임 게임 타이머 에러: roomId={}", roomId, e);
                }
            }, 1, 1, TimeUnit.SECONDS);

            log.info("에임 게임 타이머 시작: roomId={}, duration={}s", roomId, GAME_DURATION);
        } else if ("Reaction Race".equals(room.getGameName())) {
            // Reaction Race logic
            startReactionRound(roomId);
        }

        return room;
    }

    public void sendGameState(String roomId, String userId) {
        GameSession session = sessions.get(roomId);
        if (session == null)
            return;

        // 1. Send active targets
        for (GameTargetDto target : session.activeTargets.values()) {
            GameEventDto evt = new GameEventDto();
            evt.setRoomId(roomId);
            evt.setType("spawnTarget");
            evt.setTarget(target);
            evt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);
            // Note: Broadcasting to room is fine, but ideally we should send to specific
            // user
            // using separate destination like /topic/minigame/room/{roomId}/game/{userId}
            // or just rely on client filtering. But for now broadcasting "state" again to
            // everyone is messy.
            // Let's assume the frontend will dedup or we send to user specific channel if
            // possible.
            // Wait, standard STOMP pattern for user specific is /user/queue/... or specific
            // topic.
            // The user is subscribed to /topic/minigame/room/{roomId}/game.
            // If I broadcast, everyone gets it again.
            // Better: use convertAndSendToUser if we had user sessions set up affecting
            // destinations,
            // or just create a specific temporary topic.
            // However, typical simple approach: Just broadcast. Frontend "spawnTarget"
            // usually just updates map.
            // Idempotent: yes.
        }

        // 2. Send current scores
        for (Map.Entry<String, Integer> entry : session.scores.entrySet()) {
            GameEventDto scoreEvt = new GameEventDto();
            scoreEvt.setRoomId(roomId);
            scoreEvt.setType("scoreUpdate");
            scoreEvt.setPlayerId(entry.getKey());
            scoreEvt.setPayload(String.valueOf(entry.getValue()));
            scoreEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", scoreEvt);
        }
    }

    private void spawnTarget(String roomId) {
        GameSession session = sessions.get(roomId);
        if (session == null)
            return;

        // 최대 3개까지만 타겟 생성
        if (session.activeTargets.size() >= MAX_TARGETS) {
            return;
        }

        GameTargetDto target = new GameTargetDto();
        target.setId(UUID.randomUUID().toString());
        target.setX(random.nextDouble());
        target.setY(random.nextDouble());
        target.setSize(0.06 + random.nextDouble() * 0.08); // radius normalized
        target.setCreatedAt(System.currentTimeMillis());
        target.setDuration(10000); // 10s timeout, enough for players to click

        session.activeTargets.put(target.getId(), target);

        GameEventDto evt = new GameEventDto();
        evt.setRoomId(roomId);
        evt.setType("spawnTarget");
        evt.setTarget(target);
        evt.setTimestamp(System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);
        log.info("타겟 생성: roomId={}, targetId={}, 현재 타겟 수={}", roomId, target.getId(), session.activeTargets.size());
    }

    private void endGameSession(String roomId) {
        GameSession session = sessions.remove(roomId);
        if (session == null)
            return;
        if (session.future != null && !session.future.isCancelled()) {
            session.future.cancel(false);
        }

        // Broadcast final scores
        Map<String, Integer> scores = session.scores;
        GameEventDto evt = new GameEventDto();
        evt.setRoomId(roomId);
        evt.setType("gameEnd");
        evt.setPayload(scores.toString());
        evt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", evt);

        // reset room playing flag and ready states
        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            // 모든 플레이어의 준비 상태 초기화 (방장 제외)
            if (room.getPlayers() != null) {
                for (MinigamePlayerDto player : room.getPlayers()) {
                    if (!player.isHost()) {
                        player.setReady(false);
                    }
                }
            }
            // 방 상태 업데이트를 모든 클라이언트에 브로드캐스트
            room.setAction("gameEnd");
            room.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId, room);
        }
    }

    public MinigameRoomDto endGameAndResetReady(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) {
            return null;
        }

        room.setPlaying(false);

        // 모든 플레이어의 준비 상태 초기화 (방장 제외)
        if (room.getPlayers() != null) {
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) {
                    player.setReady(false);
                }
            }
        }

        log.info("게임 종료 및 준비 상태 초기화: {}", roomId);
        return room;
    }

    public synchronized GameScoreDto handleHit(String roomId, String playerId, String playerName, String targetId,
            long clientTs) {
        log.info("handleHit called: room={}, player={}, target={}", roomId, playerId, targetId);

        GameSession session = sessions.get(roomId);
        GameScoreDto result = new GameScoreDto();
        result.setPlayerId(playerId);
        result.setScore(0);

        if (session == null) {
            log.warn("Session not found for roomId: {}", roomId);
            return result;
        }

        GameTargetDto target = session.activeTargets.get(targetId);
        if (target == null) {
            log.warn("Target not found in session activeTargets. ID: {}", targetId);
            log.info("Current active targets: {}", session.activeTargets.keySet());
            return result; // already taken or expired
        }

        log.info("Target HIT! Removing target: {}", targetId);

        // hit successful
        session.activeTargets.remove(targetId);
        int newScore = session.scores.getOrDefault(playerId, 0) + 1;
        session.scores.put(playerId, newScore);
        result.setScore(newScore);

        log.info("New score for player {}: {}", playerId, newScore);

        // broadcast score update
        GameEventDto scoreEvt = new GameEventDto();
        scoreEvt.setRoomId(roomId);
        scoreEvt.setType("scoreUpdate");
        scoreEvt.setPlayerId(playerId);
        scoreEvt.setPlayerName(playerName);
        scoreEvt.setPayload(String.valueOf(newScore));
        scoreEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", scoreEvt);

        // broadcast target removed
        GameEventDto removed = new GameEventDto();
        removed.setRoomId(roomId);
        removed.setType("targetRemoved");
        removed.setTarget(target);
        removed.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", removed);

        // 타겟이 제거되었으므로 새로운 타겟 생성 (최대 3개까지)
        log.info("타겟 제거 후 새 타겟 생성 시도...");
        spawnTarget(roomId);

        return result;
    }

    // --- Reaction Race (MVP) ---
    public void startReactionRound(String roomId) {
        startReactionRound(roomId, false);
    }

    public void startReactionRound(String roomId, boolean immediate) {
        // send prepare
        GameEventDto prepare = new GameEventDto();
        prepare.setRoomId(roomId);
        prepare.setType("reactionPrepare");
        prepare.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", prepare);
        log.info("reactionPrepare sent for room {} (immediate={})", roomId, immediate);

        reactionActive.put(roomId, false);
        reactionWinner.remove(roomId);

        if (immediate) {
            // send GO immediately for testing
            reactionActive.put(roomId, true);
            GameEventDto goImmediate = new GameEventDto();
            goImmediate.setRoomId(roomId);
            goImmediate.setType("reactionGo");
            goImmediate.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", goImmediate);
            log.info("reactionGo (immediate) sent for room {}", roomId);

            // schedule end
            scheduler.schedule(() -> {
                reactionActive.remove(roomId);
                String winner = reactionWinner.get(roomId);
                GameEventDto end = new GameEventDto();
                end.setRoomId(roomId);
                end.setType("reactionEnd");
                end.setPayload(winner == null ? "" : winner);
                end.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", end);
                log.info("reactionEnd sent for room {} (immediate)", roomId);
            }, 3000, TimeUnit.MILLISECONDS);

            return;
        }

        // random delay then send GO
        int delayMs = 800 + random.nextInt(1800); // 800..2600ms
        log.info("Scheduling reactionGo for room {} in {}ms", roomId, delayMs);

        scheduler.schedule(() -> {
            reactionActive.put(roomId, true);
            GameEventDto go = new GameEventDto();
            go.setRoomId(roomId);
            go.setType("reactionGo");
            go.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", go);
            log.info("reactionGo sent for room {}", roomId);

            // set timeout to end reaction round
            scheduler.schedule(() -> {
                reactionActive.remove(roomId);
                String winner = reactionWinner.get(roomId);
                GameEventDto end = new GameEventDto();
                end.setRoomId(roomId);
                end.setType("reactionEnd");
                end.setPayload(winner == null ? "" : winner);
                end.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", end);
                log.info("reactionEnd sent for room {}", roomId);
            }, 3000, TimeUnit.MILLISECONDS); // 3s to respond

        }, delayMs, TimeUnit.MILLISECONDS);
    }

    public synchronized String handleReactionHit(String roomId, String playerId, String playerName, long clientTs) {
        Boolean active = reactionActive.get(roomId);
        if (active == null || !active)
            return null;
        if (reactionWinner.get(roomId) != null)
            return null; // already have winner

        reactionWinner.put(roomId, playerName != null ? playerName : playerId);
        reactionActive.remove(roomId);

        GameEventDto res = new GameEventDto();
        res.setRoomId(roomId);
        res.setType("reactionResult");
        res.setPlayerId(playerId);
        res.setPlayerName(playerName);
        res.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", res);

        return playerName != null ? playerName : playerId;
    }

    // ===== 오목 타이머 관련 메서드 =====

    public void initOmokGame(String roomId) {
        OmokGameSession session = new OmokGameSession(roomId);
        session.board = new int[225]; // 15x15 board
        Arrays.fill(session.board, 0);
        session.moveCount = 0;
        omokSessions.put(roomId, session);
        log.info("오목 게임 초기화: roomId={}", roomId);
    }

    public void startOmokTimer(String roomId) {
        OmokGameSession session = omokSessions.get(roomId);
        if (session == null) {
            log.warn("오목 세션을 찾을 수 없음: roomId={}", roomId);
            return;
        }

        // 기존 타이머 취소
        if (session.timerFuture != null && !session.timerFuture.isCancelled()) {
            session.timerFuture.cancel(false);
        }

        session.remainingSeconds = 15;

        // 매 1초마다 타이머 업데이트 브로드캐스트
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                OmokGameSession s = omokSessions.get(roomId);
                if (s == null)
                    return;

                s.remainingSeconds--;

                // 타이머 업데이트 브로드캐스트
                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("omokTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                // 시간 초과 시 랜덤 위치에 돌 놓기
                if (s.remainingSeconds <= 0) {
                    handleOmokTimeout(roomId);
                    if (s.timerFuture != null) {
                        s.timerFuture.cancel(false);
                    }
                }
            } catch (Exception e) {
                log.error("오목 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);

        log.info("오목 타이머 시작: roomId={}", roomId);
    }

    private void handleOmokTimeout(String roomId) {
        OmokGameSession session = omokSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null || room.getPlayers() == null || room.getPlayers().size() < 2) {
            return;
        }

        // 현재 턴 플레이어 찾기
        int currentPlayerIndex = session.moveCount % room.getPlayers().size();
        MinigamePlayerDto currentPlayer = room.getPlayers().get(currentPlayerIndex);

        // 빈 위치 찾기
        List<Integer> emptyPositions = new ArrayList<>();
        for (int i = 0; i < session.board.length; i++) {
            if (session.board[i] == 0) {
                emptyPositions.add(i);
            }
        }

        if (emptyPositions.isEmpty()) {
            log.warn("오목판에 빈 공간이 없음: roomId={}", roomId);
            return;
        }

        // 랜덤 위치 선택
        int randomPosition = emptyPositions.get(random.nextInt(emptyPositions.size()));
        int playerSymbol = currentPlayerIndex == 0 ? 1 : 2;
        session.board[randomPosition] = playerSymbol;
        session.moveCount++;

        log.info("오목 타임아웃 - 자동 배치: roomId={}, playerId={}, position={}", roomId,
                currentPlayer.getUserId(), randomPosition);

        // 자동 배치 이벤트 브로드캐스트
        GameEventDto autoMoveEvt = new GameEventDto();
        autoMoveEvt.setRoomId(roomId);
        autoMoveEvt.setType("omokMove");
        autoMoveEvt.setPlayerId(currentPlayer.getUserId());
        autoMoveEvt.setPosition(randomPosition);
        autoMoveEvt.setPayload("timeout"); // 타임아웃으로 인한 자동 배치 표시
        autoMoveEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", autoMoveEvt);

        // 다음 턴 타이머 시작
        startOmokTimer(roomId);
    }

    // 오목 게임 세션 클래스
    private static class OmokGameSession {
        private final String roomId;
        int[] board; // 15x15 = 225 cells
        int moveCount = 0;
        int remainingSeconds = 15;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>(); // 다시하기 요청한 플레이어 ID

        public OmokGameSession(String roomId) {
            this.roomId = roomId;
        }
    }

    /**
     * 오목 다시하기 요청 추가
     * @return 모든 플레이어가 동의했으면 true
     */
    public boolean addOmokRematchRequest(String roomId, String playerId) {
        OmokGameSession session = omokSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);

        if (session == null || room == null) {
            return false;
        }

        // 다시하기 요청 추가
        session.rematchRequests.add(playerId);
        log.info("오목 다시하기 요청 추가: roomId={}, playerId={}, 현재 요청 수={}/{}",
                 roomId, playerId, session.rematchRequests.size(), room.getPlayers().size());

        // 모든 플레이어가 동의했는지 확인
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            // 요청 초기화
            session.rematchRequests.clear();
            return true;
        }

        return false;
    }

    // ===== 끝말잇기 게임 관련 =====
    private final Map<String, WordChainSession> wordChainSessions = new ConcurrentHashMap<>();

    private static class WordChainSession {
        String roomId;
        List<String> wordHistory = new ArrayList<>();
        String currentWord;
        int currentPlayerIndex = 0;
        int remainingSeconds = 10;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>();

        public WordChainSession(String roomId) {
            this.roomId = roomId;
        }
    }

    public void initWordChainGame(String roomId, String startWord) {
        WordChainSession session = new WordChainSession(roomId);
        session.currentWord = startWord;
        session.wordHistory.add(startWord);
        session.currentPlayerIndex = 0;
        wordChainSessions.put(roomId, session);
        log.info("끝말잇기 게임 시작: roomId={}, startWord={}", roomId, startWord);
    }

    public void startWordChainTimer(String roomId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        // 기존 타이머 취소
        if (session.timerFuture != null && !session.timerFuture.isCancelled()) {
            session.timerFuture.cancel(false);
        }

        session.remainingSeconds = 10;

        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                WordChainSession s = wordChainSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                // 타이머 업데이트 브로드캐스트
                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("wordChainTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                // 시간 초과 시 현재 플레이어 패배
                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) {
                        s.timerFuture.cancel(false);
                    }
                    handleWordChainTimeout(roomId);
                }
            } catch (Exception e) {
                log.error("끝말잇기 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void handleWordChainTimeout(String roomId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        MinigamePlayerDto loser = room.getPlayers().get(session.currentPlayerIndex);

        // 게임 종료 이벤트
        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("wordChainEnd");
        endEvt.setPlayerId(loser.getUserId());
        endEvt.setPlayerName(loser.getUsername());
        endEvt.setPayload("timeout");
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        endWordChainGame(roomId);
    }

    public boolean submitWord(String roomId, String playerId, String word) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return false;

        // 현재 턴인지 확인
        MinigamePlayerDto currentPlayer = room.getPlayers().get(session.currentPlayerIndex);
        if (!currentPlayer.getUserId().equals(playerId)) {
            return false;
        }

        // 끝말잇기 규칙 검증
        String lastChar = getLastChar(session.currentWord);
        String firstChar = word.substring(0, 1);

        // 두음법칙 적용
        String convertedLastChar = applyDueum(lastChar);

        if (!firstChar.equals(lastChar) && !firstChar.equals(convertedLastChar)) {
            // 첫 글자가 맞지 않음
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("wordChainError");
            errorEvt.setPlayerId(playerId);
            errorEvt.setPayload("'" + convertedLastChar + "'(으)로 시작하는 단어를 입력하세요");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return false;
        }

        // 이미 사용한 단어인지 확인
        if (session.wordHistory.contains(word)) {
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("wordChainError");
            errorEvt.setPlayerId(playerId);
            errorEvt.setPayload("이미 사용한 단어입니다");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return false;
        }

        // 사전에 있는 단어인지 확인
        if (!isValidKoreanWord(word)) {
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("wordChainError");
            errorEvt.setPlayerId(playerId);
            errorEvt.setPayload("'" + word + "'은(는) 사전에 없는 단어입니다");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return false;
        }

        // 단어 저장
        session.wordHistory.add(word);
        session.currentWord = word;

        // 다음 플레이어로
        session.currentPlayerIndex = (session.currentPlayerIndex + 1) % room.getPlayers().size();

        // 성공 이벤트
        GameEventDto wordEvt = new GameEventDto();
        wordEvt.setRoomId(roomId);
        wordEvt.setType("wordChainWord");
        wordEvt.setPlayerId(playerId);
        wordEvt.setPlayerName(currentPlayer.getUsername());
        wordEvt.setPayload(word);
        wordEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", wordEvt);

        // 타이머 재시작
        startWordChainTimer(roomId);

        return true;
    }

    private String getLastChar(String word) {
        return word.substring(word.length() - 1);
    }

    private String applyDueum(String ch) {
        // 두음법칙 적용
        Map<String, String> dueumMap = new HashMap<>();
        dueumMap.put("녀", "여");
        dueumMap.put("뇨", "요");
        dueumMap.put("뉴", "유");
        dueumMap.put("니", "이");
        dueumMap.put("랴", "야");
        dueumMap.put("려", "여");
        dueumMap.put("례", "예");
        dueumMap.put("료", "요");
        dueumMap.put("류", "유");
        dueumMap.put("리", "이");
        dueumMap.put("라", "나");
        dueumMap.put("래", "내");
        dueumMap.put("로", "노");
        dueumMap.put("뢰", "뇌");
        dueumMap.put("루", "누");
        dueumMap.put("르", "느");
        return dueumMap.getOrDefault(ch, ch);
    }

    private void endWordChainGame(String roomId) {
        WordChainSession session = wordChainSessions.remove(roomId);
        if (session != null && session.timerFuture != null) {
            session.timerFuture.cancel(false);
        }

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public boolean addWordChainRematchRequest(String roomId, String playerId) {
        WordChainSession session = wordChainSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new WordChainSession(roomId);
            wordChainSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            return true;
        }
        return false;
    }

    // ===== 스무고개 게임 관련 =====
    private final Map<String, TwentyQSession> twentyQSessions = new ConcurrentHashMap<>();

    private static class TwentyQSession {
        String roomId;
        String questionerId;
        String questionerName;
        String category;
        String answer;
        int questionCount = 0;
        List<Map<String, Object>> history = new ArrayList<>();
        String currentAsker;
        String pendingQuestion;
        Set<String> rematchRequests = new HashSet<>();

        public TwentyQSession(String roomId) {
            this.roomId = roomId;
        }
    }

    public void initTwentyQGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null || room.getPlayers().isEmpty()) return;

        TwentyQSession session = new TwentyQSession(roomId);
        // 첫 번째 플레이어가 출제자
        MinigamePlayerDto questioner = room.getPlayers().get(0);
        session.questionerId = questioner.getUserId();
        session.questionerName = questioner.getUsername();

        twentyQSessions.put(roomId, session);
        log.info("스무고개 게임 초기화: roomId={}, questioner={}", roomId, session.questionerName);

        // 출제자에게 단어 선택 요청
        GameEventDto startEvt = new GameEventDto();
        startEvt.setRoomId(roomId);
        startEvt.setType("twentyQStart");
        startEvt.setPlayerId(session.questionerId);
        startEvt.setPlayerName(session.questionerName);
        startEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", startEvt);
    }

    public void setTwentyQWord(String roomId, String playerId, String category, String word) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || !session.questionerId.equals(playerId)) return;

        session.category = category;
        session.answer = word;

        // 단어 선택 완료 알림
        GameEventDto selectedEvt = new GameEventDto();
        selectedEvt.setRoomId(roomId);
        selectedEvt.setType("twentyQWordSelected");
        selectedEvt.setPayload(category);
        selectedEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", selectedEvt);

        log.info("스무고개 단어 설정: roomId={}, category={}, word={}", roomId, category, word);
    }

    public void submitTwentyQQuestion(String roomId, String playerId, String playerName, String question) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || session.answer == null) return;
        if (session.questionerId.equals(playerId)) return; // 출제자는 질문 불가

        session.questionCount++;
        session.pendingQuestion = question;
        session.currentAsker = playerId;

        // 질문 전송
        GameEventDto questionEvt = new GameEventDto();
        questionEvt.setRoomId(roomId);
        questionEvt.setType("twentyQQuestion");
        questionEvt.setPlayerId(playerId);
        questionEvt.setPlayerName(playerName);
        questionEvt.setPayload(question);
        questionEvt.setPosition(session.questionCount);
        questionEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", questionEvt);
    }

    public void answerTwentyQQuestion(String roomId, String playerId, boolean isYes) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || !session.questionerId.equals(playerId)) return;

        Map<String, Object> historyItem = new HashMap<>();
        historyItem.put("question", session.pendingQuestion);
        historyItem.put("answer", isYes);
        historyItem.put("questionNumber", session.questionCount);
        session.history.add(historyItem);

        // 답변 전송
        GameEventDto answerEvt = new GameEventDto();
        answerEvt.setRoomId(roomId);
        answerEvt.setType("twentyQAnswer");
        answerEvt.setPlayerId(session.currentAsker);
        answerEvt.setPayload(isYes ? "yes" : "no");
        answerEvt.setPosition(session.questionCount);
        answerEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", answerEvt);

        // 20개 질문 소진 시 게임 종료
        if (session.questionCount >= 20) {
            endTwentyQGame(roomId, null, false);
        }
    }

    public void guessTwentyQAnswer(String roomId, String playerId, String playerName, String guess) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null || session.answer == null) return;

        boolean correct = session.answer.equalsIgnoreCase(guess.trim());

        // 추측 결과 전송
        GameEventDto guessEvt = new GameEventDto();
        guessEvt.setRoomId(roomId);
        guessEvt.setType("twentyQGuess");
        guessEvt.setPlayerId(playerId);
        guessEvt.setPlayerName(playerName);
        guessEvt.setPayload(guess);
        guessEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", guessEvt);

        if (correct) {
            endTwentyQGame(roomId, playerId, true);
        }
    }

    private void endTwentyQGame(String roomId, String winnerId, boolean guessed) {
        TwentyQSession session = twentyQSessions.get(roomId);
        if (session == null) return;

        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("twentyQEnd");
        endEvt.setPlayerId(winnerId);
        endEvt.setPayload(session.answer);
        endEvt.setPosition(session.questionCount);
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public boolean addTwentyQRematchRequest(String roomId, String playerId) {
        TwentyQSession session = twentyQSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new TwentyQSession(roomId);
            twentyQSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            twentyQSessions.remove(roomId);
            return true;
        }
        return false;
    }

    // ===== 라이어 게임 관련 =====
    private final Map<String, LiarGameSession> liarSessions = new ConcurrentHashMap<>();

    private static class LiarGameSession {
        String roomId;
        String liarId;
        String liarName;
        String category;
        String keyword;
        Map<String, String> votes = new HashMap<>();
        int remainingSeconds = 0;
        java.util.concurrent.ScheduledFuture<?> timerFuture;
        Set<String> rematchRequests = new HashSet<>();
        boolean liarCaught = false;
        String liarGuess = null;

        public LiarGameSession(String roomId) {
            this.roomId = roomId;
        }
    }

    // 라이어 게임 카테고리 및 단어
    private static final Map<String, List<String>> LIAR_WORDS = new HashMap<>();
    static {
        LIAR_WORDS.put("동물", Arrays.asList("사자", "호랑이", "코끼리", "기린", "펭귄", "돌고래", "독수리", "판다"));
        LIAR_WORDS.put("음식", Arrays.asList("피자", "햄버거", "스파게티", "초밥", "김치찌개", "불고기", "떡볶이", "치킨"));
        LIAR_WORDS.put("직업", Arrays.asList("의사", "변호사", "소방관", "요리사", "선생님", "경찰관", "프로그래머", "디자이너"));
        LIAR_WORDS.put("장소", Arrays.asList("학교", "병원", "공원", "도서관", "영화관", "마트", "해변", "산"));
        LIAR_WORDS.put("스포츠", Arrays.asList("축구", "농구", "야구", "테니스", "수영", "골프", "배드민턴", "탁구"));
    }

    public void initLiarGame(String roomId) {
        MinigameRoomDto room = rooms.get(roomId);
        if (room == null) return;

        // 최소 4명 필요
        if (room.getPlayers().size() < 4) {
            // 에러 메시지 전송
            GameEventDto errorEvt = new GameEventDto();
            errorEvt.setRoomId(roomId);
            errorEvt.setType("liarGameError");
            errorEvt.setPayload("라이어 게임은 최소 4명의 플레이어가 필요합니다. (현재: " + room.getPlayers().size() + "명)");
            errorEvt.setTimestamp(System.currentTimeMillis());
            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", errorEvt);
            return;
        }

        LiarGameSession session = new LiarGameSession(roomId);

        // 랜덤으로 라이어 선택
        int liarIndex = random.nextInt(room.getPlayers().size());
        MinigamePlayerDto liar = room.getPlayers().get(liarIndex);
        session.liarId = liar.getUserId();
        session.liarName = liar.getUsername();

        // 랜덤 카테고리와 단어 선택
        List<String> categories = new ArrayList<>(LIAR_WORDS.keySet());
        session.category = categories.get(random.nextInt(categories.size()));
        List<String> words = LIAR_WORDS.get(session.category);
        session.keyword = words.get(random.nextInt(words.size()));

        liarSessions.put(roomId, session);
        log.info("라이어 게임 시작: roomId={}, liar={}, category={}, keyword={}",
                roomId, session.liarName, session.category, session.keyword);

        // 각 플레이어에게 역할 전송
        for (MinigamePlayerDto player : room.getPlayers()) {
            GameEventDto roleEvt = new GameEventDto();
            roleEvt.setRoomId(roomId);
            roleEvt.setType("liarGameStart");
            roleEvt.setPlayerId(player.getUserId());

            Map<String, String> roleData = new HashMap<>();
            roleData.put("category", session.category);
            if (player.getUserId().equals(session.liarId)) {
                roleData.put("role", "liar");
                roleData.put("keyword", "???");
            } else {
                roleData.put("role", "citizen");
                roleData.put("keyword", session.keyword);
            }
            roleEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(roleData).toString());
            roleEvt.setTimestamp(System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game/" + player.getUserId(), roleEvt);
        }

        // 5초 후 토론 시작
        scheduler.schedule(() -> startLiarDiscussion(roomId), 5, TimeUnit.SECONDS);
    }

    private void startLiarDiscussion(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.remainingSeconds = 60;

        // 토론 시작 알림
        GameEventDto discussEvt = new GameEventDto();
        discussEvt.setRoomId(roomId);
        discussEvt.setType("liarDiscussionStart");
        discussEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", discussEvt);

        // 타이머 시작
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                LiarGameSession s = liarSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("liarTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) s.timerFuture.cancel(false);
                    startLiarVoting(roomId);
                }
            } catch (Exception e) {
                log.error("라이어 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void startLiarVoting(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.votes.clear();
        session.remainingSeconds = 15;

        // 투표 시작 알림
        GameEventDto voteEvt = new GameEventDto();
        voteEvt.setRoomId(roomId);
        voteEvt.setType("liarVotingStart");
        voteEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", voteEvt);

        // 투표 타이머
        session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
            try {
                LiarGameSession s = liarSessions.get(roomId);
                if (s == null) return;

                s.remainingSeconds--;

                GameEventDto timerEvt = new GameEventDto();
                timerEvt.setRoomId(roomId);
                timerEvt.setType("liarTimer");
                timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                timerEvt.setTimestamp(System.currentTimeMillis());
                messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                if (s.remainingSeconds <= 0) {
                    if (s.timerFuture != null) s.timerFuture.cancel(false);
                    processLiarVotes(roomId);
                }
            } catch (Exception e) {
                log.error("라이어 투표 타이머 에러: roomId={}", roomId, e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    public void submitLiarVote(String roomId, String voterId, String targetId) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        session.votes.put(voterId, targetId);
        log.info("라이어 투표: roomId={}, voter={}, target={}", roomId, voterId, targetId);

        // 투표 현황 브로드캐스트
        GameEventDto voteEvt = new GameEventDto();
        voteEvt.setRoomId(roomId);
        voteEvt.setType("liarVote");
        voteEvt.setPlayerId(voterId);
        voteEvt.setPayload(targetId);
        voteEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", voteEvt);

        // 모든 플레이어가 투표했는지 확인
        MinigameRoomDto room = rooms.get(roomId);
        if (room != null && session.votes.size() >= room.getPlayers().size()) {
            if (session.timerFuture != null) session.timerFuture.cancel(false);
            processLiarVotes(roomId);
        }
    }

    private void processLiarVotes(String roomId) {
        LiarGameSession session = liarSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null || room == null) return;

        // 득표수 계산
        Map<String, Integer> voteCounts = new HashMap<>();
        for (String targetId : session.votes.values()) {
            voteCounts.merge(targetId, 1, Integer::sum);
        }

        // 최다 득표자 찾기
        String mostVotedId = null;
        int maxVotes = 0;
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() > maxVotes) {
                maxVotes = entry.getValue();
                mostVotedId = entry.getKey();
            }
        }

        // 라이어가 잡혔는지 확인
        boolean liarCaught = mostVotedId != null && mostVotedId.equals(session.liarId);
        session.liarCaught = liarCaught;

        // 투표 결과 전송
        GameEventDto resultEvt = new GameEventDto();
        resultEvt.setRoomId(roomId);
        resultEvt.setType("liarVoteResult");
        resultEvt.setPlayerId(mostVotedId);

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("votedPlayerId", mostVotedId);
        resultData.put("voteCount", maxVotes);
        resultData.put("liarCaught", liarCaught);
        resultData.put("liarId", session.liarId);
        resultData.put("liarName", session.liarName);
        resultData.put("voteCounts", voteCounts);

        try {
            resultEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(resultData));
        } catch (Exception e) {
            resultEvt.setPayload(resultData.toString());
        }
        resultEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", resultEvt);

        // 라이어가 잡혔으면 키워드 맞추기 기회 (10초)
        if (liarCaught) {
            session.remainingSeconds = 10;
            session.timerFuture = scheduler.scheduleAtFixedRate(() -> {
                try {
                    LiarGameSession s = liarSessions.get(roomId);
                    if (s == null) return;
                    s.remainingSeconds--;

                    GameEventDto timerEvt = new GameEventDto();
                    timerEvt.setRoomId(roomId);
                    timerEvt.setType("liarTimer");
                    timerEvt.setPayload(String.valueOf(s.remainingSeconds));
                    timerEvt.setTimestamp(System.currentTimeMillis());
                    messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", timerEvt);

                    if (s.remainingSeconds <= 0) {
                        if (s.timerFuture != null) s.timerFuture.cancel(false);
                        endLiarGame(roomId, false); // 라이어가 맞추지 못함
                    }
                } catch (Exception e) {
                    log.error("라이어 키워드 맞추기 타이머 에러: roomId={}", roomId, e);
                }
            }, 1, 1, TimeUnit.SECONDS);
        } else {
            // 라이어가 못 잡혔으면 라이어 승리
            endLiarGame(roomId, true);
        }
    }

    public void submitLiarGuess(String roomId, String playerId, String guess) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null || !session.liarId.equals(playerId)) return;

        session.liarGuess = guess;
        boolean correct = session.keyword.equals(guess.trim());

        if (session.timerFuture != null) session.timerFuture.cancel(false);

        // 추측 결과 전송
        GameEventDto guessEvt = new GameEventDto();
        guessEvt.setRoomId(roomId);
        guessEvt.setType("liarGuessResult");
        guessEvt.setPlayerId(playerId);

        Map<String, Object> guessData = new HashMap<>();
        guessData.put("guess", guess);
        guessData.put("correct", correct);
        guessData.put("keyword", session.keyword);

        try {
            guessEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(guessData));
        } catch (Exception e) {
            guessEvt.setPayload(guessData.toString());
        }
        guessEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", guessEvt);

        // 라이어가 키워드를 맞추면 라이어 승리
        endLiarGame(roomId, correct);
    }

    private void endLiarGame(String roomId, boolean liarWins) {
        LiarGameSession session = liarSessions.get(roomId);
        if (session == null) return;

        if (session.timerFuture != null) session.timerFuture.cancel(false);

        GameEventDto endEvt = new GameEventDto();
        endEvt.setRoomId(roomId);
        endEvt.setType("liarGameEnd");

        Map<String, Object> endData = new HashMap<>();
        endData.put("liarWins", liarWins);
        endData.put("liarId", session.liarId);
        endData.put("liarName", session.liarName);
        endData.put("keyword", session.keyword);
        endData.put("category", session.category);

        try {
            endEvt.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(endData));
        } catch (Exception e) {
            endEvt.setPayload(endData.toString());
        }
        endEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", endEvt);

        MinigameRoomDto room = rooms.get(roomId);
        if (room != null) {
            room.setPlaying(false);
            for (MinigamePlayerDto player : room.getPlayers()) {
                if (!player.isHost()) player.setReady(false);
            }
        }
    }

    public void sendLiarChat(String roomId, String playerId, String playerName, String message) {
        GameEventDto chatEvt = new GameEventDto();
        chatEvt.setRoomId(roomId);
        chatEvt.setType("liarChat");
        chatEvt.setPlayerId(playerId);
        chatEvt.setPlayerName(playerName);
        chatEvt.setPayload(message);
        chatEvt.setTimestamp(System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/minigame/room/" + roomId + "/game", chatEvt);
    }

    public boolean addLiarRematchRequest(String roomId, String playerId) {
        LiarGameSession session = liarSessions.get(roomId);
        MinigameRoomDto room = rooms.get(roomId);
        if (session == null) {
            session = new LiarGameSession(roomId);
            liarSessions.put(roomId, session);
        }
        if (room == null) return false;

        session.rematchRequests.add(playerId);
        if (session.rematchRequests.size() >= room.getPlayers().size()) {
            session.rematchRequests.clear();
            liarSessions.remove(roomId);
            return true;
        }
        return false;
    }
}

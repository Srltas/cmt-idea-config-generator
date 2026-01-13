# CMT IDEA Config Generator

Eclipse RCP 프로젝트를 IntelliJ IDEA에서 개발할 수 있도록 IDEA 설정 파일을 자동 생성하는 도구입니다.

## 개요

Eclipse RCP(Rich Client Platform) 프로젝트는 OSGi 번들 기반으로 구성되어 있어 IntelliJ IDEA에서 직접 개발하기 어렵습니다. 이 도구는 Eclipse RCP 프로젝트의 구조를 분석하여 IntelliJ IDEA 프로젝트 설정 파일을 자동으로 생성합니다.

## 요구사항

- Java 21 이상
- Maven 3.6 이상

## 빌드

```bash
mvn clean package
```

빌드 후 `target/cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar` 파일이 생성됩니다.

## 사용법

### 명령줄 옵션

| 옵션 | 설명 | 필수 |
|------|------|------|
| `-c, --config` | 설정 파일 경로 (예: `osgi-app.properties`) | O |
| `-p, --projects-folder` | Eclipse RCP 프로젝트 루트 폴더 | O |
| `-o, --output` | IDEA 설정 파일 출력 디렉토리 | O |
| `-e, --eclipse` | Eclipse 의존성 폴더 (기본값: `<projects-folder>/../workspace/dependencies`) | X |
| `-d, --debug` | 상세 디버그 로그 출력 | X |

### 사용 예시

```bash
# 기본 실행
java -jar cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar \
  -c /path/to/osgi-app.properties \
  -p /path/to/eclipse-rcp-project \
  -o /path/to/output \
  -e /path/to/eclipse-plugins
```

## 출력 구조

생성되는 IDEA 프로젝트 구조:

```
<output-directory>/
└── <workspaceName>/
    ├── .idea/
    │   ├── modules.xml          # 모듈 목록
    │   ├── libraries/           # 외부 라이브러리 설정
    │   │   └── *.xml
    │   └── runConfigurations/   # 실행 구성
    │       └── *.xml
    ├── modules/                  # 모듈 파일
    │   └── *.iml
    └── runtime/                  # OSGi 런타임 파일
        ├── config.ini
        └── dev.properties
```

## Eclipse 의존성 설정

Eclipse RCP 프로젝트의 외부 의존성(Eclipse 플랫폼 플러그인 등)은 별도의 폴더에 위치해야 합니다. 기본 위치는 `<projects-folder>/../workspace/dependencies`입니다.

의존성 폴더에는 Eclipse Target Platform에서 추출한 플러그인들이 위치해야 합니다:

```
dependencies/
├── org.eclipse.core.runtime_3.x.x.jar
├── org.eclipse.ui_3.x.x.jar
├── org.eclipse.swt_3.x.x.jar
└── ...
```

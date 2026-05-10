# CMT IDEA Config Generator

Eclipse RCP 프로젝트를 IntelliJ IDEA에서 개발할 수 있도록 IDEA 설정 파일을 자동 생성하는 도구입니다.

## 개요

Eclipse RCP(Rich Client Platform) 프로젝트는 OSGi 번들 기반으로 구성되어 있어 IntelliJ IDEA에서 직접 개발하기 어렵습니다. 이 도구는 Eclipse RCP 프로젝트의 구조를 분석하여 IntelliJ IDEA 프로젝트 설정 파일을 자동으로 생성합니다.

**Desktop(GUI/OSGi)과 Console(순수 Java) 두 가지 실행 모드를 동시에 지원합니다.**
**Maven 기반 테스트 모듈(JUnit, Mockito 등)도 IDEA 모듈로 등록하여 IDE에서 직접 실행/디버깅할 수 있습니다.**

## 요구사항

- Java 21 이상
- Maven 3.6 이상

## 빌드

```bash
mvn clean package
```

빌드 후 `target/cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar` 파일이 생성됩니다.

## 사용법

### 빠른 실행 (runGenerator.sh)

프로젝트 루트의 스크립트를 사용하면 빌드 후 즉시 실행할 수 있습니다:

```bash
./runGenerator.sh
```

`osgi-app.properties`와 프로젝트 경로가 스크립트에 미리 설정되어 있습니다. 추가 옵션은 인자로 전달할 수 있습니다:

```bash
./runGenerator.sh --debug
./runGenerator.sh --dry-run
```

### 명령줄 옵션

| 옵션 | 설명 | 필수 |
|------|------|------|
| `-c, --config` | 설정 파일 경로 (예: `osgi-app.properties`) | O |
| `-p, --projects-folder` | Eclipse RCP 프로젝트 루트 폴더 | O |
| `-o, --output` | IDEA 설정 파일 출력 디렉토리 | O |
| `-e, --eclipse` | Eclipse 의존성 폴더 (기본값: `<projects-folder>/../workspace/dependencies`) | X |
| `-n, --dry-run` | 파일 생성 없이 분석만 수행 | X |
| `-d, --debug` | 상세 디버그 로그 출력 | X |

### 사용 예시

```bash
# 기본 실행
java -jar cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar \
  -c /path/to/osgi-app.properties \
  -p /path/to/eclipse-rcp-project \
  -o /path/to/output \
  -e /path/to/eclipse-plugins

# 분석만 수행 (파일 미생성)
java -jar cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar \
  -c osgi-app.properties \
  -p /path/to/project \
  -o /path/to/output \
  --dry-run --debug
```

## 출력 구조

생성되는 IDEA 프로젝트 구조:

```
<output-directory>/
└── <workspaceName>/
    ├── .idea/
    │   ├── modules.xml                    # 모듈 목록
    │   ├── libraries/                     # 외부 라이브러리 설정
    │   │   └── *.xml                      # 번들별 라이브러리 (JAR 경로 포함)
    │   └── runConfigurations/             # 실행 구성
    │       ├── CMT_Desktop.xml            # OSGi/Eclipse RCP 실행 (Equinox Launcher)
    │       └── CMT_Console.xml            # Console 앱 실행 (Main-Class 직접 실행)
    ├── modules/                           # 모듈 파일
    │   └── *.iml                          # 번들별 의존성 설정
    └── runtime/                           # OSGi 런타임 파일 (Desktop 전용)
        ├── dev.properties                 # 번들별 클래스 출력 경로
        └── configuration/
            ├── config.ini                 # Eclipse 시작 설정
            └── org.eclipse.equinox.simpleconfigurator/
                └── bundles.info           # OSGi 번들 목록 및 시작 레벨
```

## 두 가지 실행 모드

### Desktop (OSGi/Eclipse RCP)

Eclipse Equinox Launcher를 통해 OSGi 런타임 위에서 실행되는 GUI 애플리케이션입니다.

- **실행 구성 파일**: `CMT_Desktop.xml`
- **진입점**: `org.eclipse.equinox.launcher.Main`
- **런타임 파일**: `dev.properties`, `config.ini`, `bundles.info` 자동 생성
- **플랫폼별 VM 인자**: macOS에서 `-XstartOnFirstThread` 자동 추가

### Console (순수 Java)

OSGi 없이 `Main-Class`를 직접 실행하는 커맨드라인 애플리케이션입니다.

- **실행 구성 파일**: `CMT_Console.xml`
- **자동 감지**: `bundlesPaths` 아래에 있는 번들 중 MANIFEST.MF의 `Main-Class` 헤더가 있는 모듈을 자동 인식 (별도 등록 불필요)
- **Bundle-SymbolicName 없는 모듈 지원**: MANIFEST.MF에 `Bundle-SymbolicName`이 없으면 디렉토리 이름을 모듈 이름으로 사용
- **pom.xml 의존성 분석**: standalone 모듈의 `pom.xml`을 파싱하여 의존하는 로컬 모듈을 자동으로 classpath에 포함

## Maven 테스트 모듈 (Test Modules)

`pom.xml`을 갖는 Maven 스타일 테스트 모듈(예: `tests/unit-test`, `tests/e2e`)을 IDEA 모듈로 등록하여 IDE에서 직접 테스트를 실행/디버깅할 수 있습니다.

`osgi-app.properties`의 `testModuleRoots`에 등록:

```properties
testModuleRoots=\
  tests/unit-test;\
  tests/e2e
```

각 테스트 모듈에 대해 다음을 자동 생성합니다:

- `src/test/java`, `src/test/resources`(있을 경우 `src/main/java/resources`도) 소스 루트 (테스트 소스/리소스로 마킹)
- `<groupId>com.cubrid.cubridmigration</groupId>`로 시작하면서 로컬 OSGi 번들과 일치하는 의존성은 IDEA 모듈 의존성(TEST 스코프)으로 추가
- 그 외 외부 Maven 의존성은 두 가지 방법으로 해결:
  1. 우선 `mvn dependency:build-classpath`를 호출하여 BOM, transitive까지 정확히 해결
  2. Maven 호출 실패 시 `~/.m2/repository`에서 직접 lookup (transitive 제외)

> 테스트 모듈이 로컬 SNAPSHOT 번들에 의존하는 경우, Maven 빌드가 실패할 수 있어 fallback 경로가 사용됩니다. 가능한 한 정확한 transitive 의존성을 얻으려면 한 번 `mvn install -Punit-test -DskipTests` 등으로 로컬 저장소에 SNAPSHOT을 미리 설치해 두세요.

## 의존성 해결 메커니즘

### OSGi 번들 의존성

`Require-Bundle` 및 `Import-Package` 헤더를 파싱하여 번들 간 의존성 그래프를 구성합니다.

### 외부 번들 re-export 추적

IntelliJ IDEA는 OSGi classloader가 없어 `visibility:=reexport`로 선언된 번들을 자동으로 전파하지 않습니다. 이 도구는 외부 JAR의 MANIFEST.MF를 직접 파싱하여 re-export 체인을 추적하고, 각 모듈의 classpath에 필요한 외부 번들을 정확하게 포함합니다.

예: `org.eclipse.ui`가 `org.eclipse.swt`, `org.eclipse.jface`, `org.eclipse.core.commands` 등을 re-export하는 경우, `org.eclipse.ui`에 의존하는 모듈에는 이 모든 번들이 자동으로 포함됩니다.

### OSGi Fragment 지원

`Fragment-Host` 헤더를 파싱하여 OSGi Fragment 번들을 처리합니다. 예를 들어 `org.eclipse.swt` JAR는 실제 클래스가 없고, 플랫폼별 Fragment (`org.eclipse.swt.cocoa.macosx.aarch64` 등)에 실제 구현이 있습니다. 이 도구는 Fragment를 자동으로 감지하여 Host 번들에 의존하는 모듈의 classpath에 포함합니다.

### 모듈별 최소 의존성

모든 모듈에 동일한 외부 라이브러리를 포함하는 대신, 각 모듈이 실제로 필요한 외부 번들만 정밀하게 계산합니다. 이를 통해 ServiceLoader 충돌(예: `M2ELogbackConfigurator`) 등의 문제를 방지합니다.

- `org.eclipse.equinox.launcher`는 Desktop 앱 모듈에만 포함
- `org.eclipse.m2e.*` 번들은 Eclipse IDE 전용으로 자동 제외

## Eclipse 의존성 설정

Eclipse RCP 프로젝트의 외부 의존성(Eclipse 플랫폼 플러그인, CUBRID 번들 등)은 별도 폴더에 위치해야 합니다.

기본 위치: `<projects-folder>/../workspace/dependencies`

또는 `-e` 옵션으로 직접 지정:

```bash
java -jar cmt-idea-config-generator.jar ... -e /path/to/eclipse-plugins
```

의존성 폴더 구조:

```
dependencies/
├── plugins/                               # Eclipse Target Platform에서 추출한 플러그인
│   ├── org.eclipse.core.runtime_3.x.x.jar
│   ├── org.eclipse.ui_3.x.x.jar
│   ├── org.eclipse.swt_3.x.x.jar
│   ├── org.eclipse.swt.cocoa.macosx.aarch64_3.x.x.jar   # macOS SWT Fragment
│   └── ...
└── ...
```

## 처리 흐름

```
1. 설정 파일 로드 (osgi-app.properties)
2. 번들/Feature/Product 파싱
   ├── MANIFEST.MF 파싱 (Bundle-SymbolicName, Require-Bundle, Main-Class 등)
   ├── build.properties 파싱 (소스 폴더, 출력 폴더)
   └── pom.xml 파싱 (standalone 모듈의 의존성)
3. 의존성 해결
   ├── 외부 JAR MANIFEST.MF 파싱 (re-export 체인, Fragment-Host 추출)
   ├── 번들 간 의존성 그래프 구성
   └── 위상 정렬로 번들 순서 결정
4. IDEA 설정 파일 생성
   ├── *.iml (모듈별 의존성, 소스/출력 경로)
   ├── modules.xml (모듈 목록)
   ├── libraries/*.xml (외부 라이브러리 JAR 경로)
   ├── runConfigurations/CMT_Desktop.xml (OSGi 실행 구성)
   ├── runConfigurations/CMT_Console.xml (Console 실행 구성, standalone 앱이 있을 때)
   ├── runtime/dev.properties (OSGi 개발 모드 클래스 경로)
   └── runtime/configuration/config.ini + bundles.info (OSGi 번들 시작 설정)
```

## 알려진 제한사항

- **플랫폼 특정 SWT Fragment**: macOS, Windows, Linux 각각에 맞는 SWT Fragment JAR가 의존성 폴더에 있어야 합니다. Fragment가 없으면 SWT 클래스를 찾을 수 없습니다.
- **config.ini 플랫폼 의존성**: `osgi.install.area` 경로는 절대 경로로 생성되므로, 다른 개발자와 공유할 경우 재생성이 필요합니다.
- **macOS VM 인자**: `-XstartOnFirstThread`는 생성 시점의 OS를 기준으로 추가됩니다. Windows/Linux 환경에서 생성한 설정을 macOS에서 사용하려면 수동으로 추가해야 합니다.

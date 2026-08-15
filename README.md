# CMT IDEA Config Generator

CUBRID Migration Toolkit(Eclipse RCP 프로젝트)을 IntelliJ IDEA에서 실행하고 디버깅할 수 있도록 IDEA 프로젝트 설정을 자동 생성하는 도구입니다.

Eclipse RCP는 OSGi 번들 기반이라 IDEA가 그대로 인식하지 못합니다. 이 도구가 번들 구조와 의존성을 분석해 `.iml`, 라이브러리, 실행 구성, OSGi 런타임 파일까지 만들어 줍니다.

---

## 빠른 시작

**1. CMT를 한 번 빌드합니다.** (Tycho가 Eclipse 번들을 로컬 Maven 저장소에 내려받습니다)

```bash
mvn -f /path/to/cmt package -DskipTests
```

**2. 생성기를 실행합니다.**

```bash
./runGenerator.sh          # macOS / Linux
runGenerator.bat           # Windows
```

**3. IDEA에서 생성된 폴더를 엽니다.**

```
/path/to/cmt/../cubrid-migration-idea
```

처음 열 때 `File → Project Structure → Project → SDK`에서 **Java 21**을 한 번 지정하면 끝입니다. 우측 상단 실행 구성에 **CMT Desktop**(GUI)과 **CMT Console**(CLI)이 준비되어 있고, `plugins/*/src`의 소스에 브레이크포인트가 그대로 걸립니다.

> Eclipse IDE 설치는 필요 없습니다. 필요한 Eclipse 번들은 1번 단계에서 받은 Maven 캐시에서 가져옵니다.

## 무엇이 만들어지는가

```
cubrid-migration-idea/              ← IDEA 로 여는 폴더
├── .idea/
│   ├── modules.xml                 모듈 목록
│   ├── libraries/*.xml             외부 번들 라이브러리
│   └── runConfigurations/
│       ├── CMT_Desktop.xml         OSGi/RCP GUI 실행
│       └── CMT_Console.xml         Main-Class 직접 실행
├── modules/*.iml                   번들별 소스·출력·의존성
├── logback.xml                     Console 실행 시 로그 설정
└── runtime/                        OSGi 런타임 (Desktop 전용)
    ├── dev.properties              번들별 클래스 출력 경로
    └── configuration/
        ├── config.ini
        └── org.eclipse.equinox.simpleconfigurator/bundles.info

<cmt>/../workspace/dependencies/    Maven p2 캐시에서 구성한 Eclipse 번들
```

소스는 복사되지 않습니다. 각 모듈은 `plugins/<번들>`을 콘텐츠 루트로 참조하므로, IDEA에서 고친 코드가 곧바로 실행에 반영됩니다.

## 명령줄

인자는 프로젝트 폴더 하나뿐이고 나머지는 전부 기본값이 있습니다.

```bash
java -jar target/cmt-idea-config-generator-1.0.0-SNAPSHOT-all.jar /path/to/cmt
```

| 인자 / 옵션 | 설명 | 기본값 |
|---|---|---|
| `<project-dir>` | Eclipse RCP 프로젝트 루트 (필수) | — |
| `-o, --output` | IDEA 프로젝트를 만들 위치 | `<project-dir>`의 상위 |
| `-m, --maven-repo` | Tycho p2 캐시를 가진 로컬 Maven 저장소 | `<user.home>/.m2/repository` |
| `-n, --dry-run` | 파일 생성 없이 분석만 | off |
| `-d, --debug` | 상세 로그 | off |

`runGenerator.sh` / `.bat`은 생성기를 빌드한 뒤 실행합니다. 프로젝트 경로는 **첫 번째 인자 → `$CMT_HOME` → 형제 디렉토리 자동 탐색** 순으로 찾고, 나머지 인자는 생성기로 그대로 넘깁니다.

```bash
./runGenerator.sh /path/to/cmt
./runGenerator.sh --debug
```

## 두 가지 실행 모드

### CMT Desktop (OSGi/RCP)

Equinox Launcher로 OSGi 런타임을 띄우는 GUI 애플리케이션입니다.

- 진입점: `org.eclipse.equinox.launcher.Main`
- `dev.properties`가 각 번들의 클래스 경로를 `plugins/<번들>/bin`(IDEA 컴파일 출력)으로 지정하므로, dev 클래스가 로드됩니다
- `bundles.info`의 시작 플래그는 실제 배포 제품과 동일합니다. 애플리케이션 번들은 `eclipse.application`이 활성화하므로 autostart 하지 않습니다
- macOS에서는 `-XstartOnFirstThread`가 자동으로 붙습니다

### CMT Console (순수 Java)

OSGi 없이 `Main-Class`를 직접 실행합니다.

- MANIFEST.MF에 `Main-Class` 헤더가 있는 번들을 자동 인식합니다
- `Bundle-SymbolicName`이 없으면 디렉토리 이름을 모듈 이름으로 사용합니다
- standalone 모듈의 `pom.xml`을 파싱해 의존하는 로컬 모듈을 classpath에 포함합니다
- 번들 루트의 `logback.xml`을 작업 디렉토리로 복사합니다. OSGi 모드는 번들 클래스패스에서 읽지만 Console은 작업 디렉토리에서 읽기 때문입니다

## 프로젝트 구조 인식

설정 파일 없이 프로젝트 폴더를 직접 훑어서 결정합니다.

| 항목 | 인식 방법 |
|---|---|
| 번들 | `plugins/` 디렉토리 |
| Feature | `features/` 디렉토리 |
| Product | 프로젝트 하위(깊이 3)의 `*.product` (`target/`, `bin/` 제외) |
| 테스트 모듈 | `tests/` 바로 아래에서 `pom.xml`을 가진 디렉토리 |

`plugins/`가 없거나 `*.product`를 찾지 못하면 오류로 중단합니다. Product가 여러 개면 실행 구성 이름에 product id가 붙습니다.

## Eclipse 번들 자동 구성

외부 의존성(Eclipse 플랫폼 플러그인, CUBRID 번들 등)은 **Maven p2 캐시에서 자동 구성**되므로 별도 준비가 필요 없습니다.

```
~/.m2/repository/p2/osgi/bundle/<name>/<version>/<name>-<version>.jar
        ↓ source 번들 제외
<project-dir>/../workspace/dependencies/<name>_<version>.jar
```

이 캐시는 CMT를 Maven으로 빌드할 때 Tycho가 타깃 플랫폼(`pom.xml`의 p2 저장소)에서 내려받은 것입니다. 즉 **CMT 빌드에 쓰인 것과 정확히 같은 번들**이며, Eclipse IDE 설치본과는 무관합니다. 모든 플랫폼(Windows/Linux/macOS, x86_64/aarch64)의 SWT fragment가 함께 들어 있어 하나의 폴더를 어느 OS에서든 쓸 수 있습니다.

두 가지 변환이 적용됩니다.

- **파일명**: p2의 `name-version.jar` → Eclipse 관례인 `name_version.jar`
- **중첩 JAR 번들 해제**: `Bundle-ClassPath`가 `lib/*.jar`를 참조하는 번들(예: `com.cubrid.bundle.*`)은 폴더로 풀어냅니다. IntelliJ IDEA는 JAR 안의 JAR를 클래스패스로 읽지 못하기 때문이며, Eclipse가 같은 번들을 unpack해서 배포하는 이유와 같습니다

이미 있는 항목은 건드리지 않으므로 재실행해도 안전하고, 손으로 추가한 번들도 유지됩니다.

## 의존성 해결 메커니즘

**OSGi 번들 의존성** — `Require-Bundle`과 `Import-Package` 헤더로 번들 간 그래프를 만들고 위상 정렬합니다.

**외부 번들 re-export 추적** — IDEA에는 OSGi classloader가 없어 `visibility:=reexport`가 자동 전파되지 않습니다. 외부 JAR의 MANIFEST.MF를 직접 파싱해 re-export 체인을 따라가 필요한 번들을 각 모듈 classpath에 넣습니다. 예를 들어 `org.eclipse.ui`에 의존하면 그것이 re-export 하는 `org.eclipse.swt`, `org.eclipse.jface` 등이 함께 포함됩니다.

**OSGi Fragment** — `Fragment-Host`를 파싱합니다. `org.eclipse.swt` JAR에는 실제 네이티브 구현이 없고 플랫폼별 Fragment(`org.eclipse.swt.cocoa.macosx.aarch64` 등)에 들어 있으므로, Host에 의존하는 모듈에 Fragment를 함께 넣습니다.

**모듈별 최소 의존성** — 모든 모듈에 같은 라이브러리를 넣지 않고 각 모듈이 실제로 필요한 것만 계산합니다. ServiceLoader 충돌(예: `M2ELogbackConfigurator`)을 막기 위해서입니다. `org.eclipse.equinox.launcher`는 Desktop 앱 모듈에만, `org.eclipse.m2e.*`는 Eclipse IDE 전용이라 제외합니다.

## Maven 테스트 모듈

`tests/` 아래에서 `pom.xml`을 가진 모듈(예: `tests/unit-test`, `tests/e2e`)을 자동으로 찾아 IDEA 모듈로 등록합니다. 각 모듈에 대해

- `src/test/java`, `src/test/resources`를 테스트 소스/리소스 루트로 마킹
- `com.cubrid.cubridmigration`으로 시작하면서 로컬 번들과 이름이 일치하는 의존성은 모듈 의존성(TEST 스코프)으로 연결
- 그 외 Maven 의존성은 `mvn dependency:build-classpath`로 해결하고, 실패하면 `~/.m2/repository`에서 직접 찾습니다(transitive 제외)

> 테스트 모듈이 로컬 SNAPSHOT 번들에 의존하면 Maven 호출이 실패해 fallback이 쓰일 수 있습니다. 정확한 transitive 의존성이 필요하면 `mvn install -DskipTests`로 SNAPSHOT을 로컬 저장소에 먼저 설치해 두세요.

## 요구사항

- Java 21 이상
- Maven 3.6 이상
- CMT 프로젝트를 한 번 이상 Maven 빌드해 둘 것

## 알려진 제한사항

- **절대 경로**: `config.ini`, `bundles.info`, `dev.properties`, 라이브러리 설정이 모두 절대 경로입니다. 다른 개발자와 공유하거나 경로를 옮기면 재생성해야 합니다.
- **macOS VM 인자**: `-XstartOnFirstThread`는 생성 시점의 OS 기준으로 추가됩니다. Windows/Linux에서 만든 설정을 macOS에서 쓰려면 재생성해야 합니다.
- **JDBC 드라이버**: CMT는 `<osgi.install.area>/jdbc`에서 드라이버를 읽습니다. 기본 구성에서는 `<project-dir>/../workspace/jdbc`이며, 필요한 드라이버는 직접 넣거나 CMT UI에서 등록해야 합니다.
- **IDEA Project SDK**: 생성물에 `misc.xml`이 없으므로 프로젝트를 처음 열 때 SDK(Java 21)를 한 번 지정해야 합니다.

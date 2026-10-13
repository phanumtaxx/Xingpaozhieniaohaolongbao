#include <windows.h>
#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <array>
#include <cmath>
#include <functional>
#include <chrono>
#include <condition_variable>
#include <cstring>
#include <filesystem>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include "include/cef_app.h"
#include "include/cef_client.h"
#include "include/cef_parser.h"
#include "include/cef_task.h"
#include "WebShaders.h"
#include <map>
#include <deque>

namespace xing::web {
class UiTask final : public CefTask {
public:
    explicit UiTask(std::function<void()> action) : action_(std::move(action)) {}
    void Execute() override { action_(); }
private:
    std::function<void()> action_;
    IMPLEMENT_REFCOUNTING(UiTask);
};
void postUi(std::function<void()> action) { CefPostTask(TID_UI,new UiTask(std::move(action))); }

class SurfaceClient final : public CefClient, public CefRenderHandler, public CefLifeSpanHandler, public CefLoadHandler {
public:
    SurfaceClient(int width,int height) : width_(width),height_(height) {}
    CefRefPtr<CefRenderHandler> GetRenderHandler() override { return this; }
    CefRefPtr<CefLifeSpanHandler> GetLifeSpanHandler() override { return this; }
    CefRefPtr<CefLoadHandler> GetLoadHandler() override { return this; }
    void GetViewRect(CefRefPtr<CefBrowser>,CefRect& rectangle) override { rectangle=CefRect(0,0,width_,height_); }
    void OnAfterCreated(CefRefPtr<CefBrowser> browser) override {
        browser_=browser;
        if (editorInput_) browser_->GetHost()->SetFocus(true);
        if(closing_) browser_->GetHost()->CloseBrowser(true);
    }
    void OnBeforeClose(CefRefPtr<CefBrowser>) override {
        browser_=nullptr;
        std::lock_guard lock(closeMutex_); closed_=true; closeCondition_.notify_all();
    }
    void OnLoadEnd(CefRefPtr<CefBrowser>,CefRefPtr<CefFrame> frame,int) override {
        if(frame->IsMain()) {
            ready_=true;
            for(const auto& [id,source]:extensions_) frame->ExecuteJavaScript(source,"xing:extension:"+id,1);
        }
    }
    void OnLoadError(CefRefPtr<CefBrowser>,CefRefPtr<CefFrame> frame,ErrorCode code,const CefString& text,const CefString&) override {
        if(frame->IsMain()&&code!=ERR_ABORTED) { std::lock_guard lock(pixelsMutex_); error_=text.ToString(); }
    }
    bool OnBeforePopup(CefRefPtr<CefBrowser>,CefRefPtr<CefFrame>,int,const CefString&,const CefString&,
        WindowOpenDisposition,bool,const CefPopupFeatures&,CefWindowInfo&,CefRefPtr<CefClient>&,
        CefBrowserSettings&,CefRefPtr<CefDictionaryValue>&,bool*) override { return true; }
    bool OnProcessMessageReceived(CefRefPtr<CefBrowser>,CefRefPtr<CefFrame> frame,CefProcessId process,CefRefPtr<CefProcessMessage> message) override {
        if (process != PID_RENDERER || !frame->IsMain()) return false;
        if (message->GetName() == CefString("xing-editor")) {
            if (!editorInput_ || message->GetArgumentList()->GetSize() != 1) return true;
            const auto command = message->GetArgumentList()->GetString(0).ToString();
            if (command.size() > 4096) return true;
            std::lock_guard lock(commandMutex_);
            if (commands_.size() < 128) commands_.push_back(command);
            return true;
        }
        if (message->GetName() != CefString("xing-backdrops")) return false;
        const auto geometry=message->GetArgumentList()->GetString(0);
        if(geometry.length()>131072) return true;
        auto parsed=CefParseJSON(geometry,JSON_PARSER_RFC);
        if(!parsed || parsed->GetType()!=VTYPE_LIST) return true;
        auto list=parsed->GetList();
        std::vector<std::array<float,5>> rectangles;
        for(std::size_t i=0;i<list->GetSize() && i<256;i++) {
            auto values=list->GetList(i);
            if(!values || values->GetSize()!=5) continue;
            std::array<float,5> rectangle={};bool valid=true;
            for(int j=0;j<5;j++) {
                if(values->GetType(j)!=VTYPE_DOUBLE && values->GetType(j)!=VTYPE_INT) {valid=false;break;}
                rectangle[j]=static_cast<float>(values->GetType(j)==VTYPE_DOUBLE?values->GetDouble(j):values->GetInt(j));
                if(!std::isfinite(rectangle[j]) || std::abs(rectangle[j])>65536) {valid=false;break;}
            }
            if(valid && rectangle[2]>0 && rectangle[3]>0 && rectangle[4]>=0) rectangles.push_back(rectangle);
        }
        bool changed;
        {
            std::lock_guard lock(pixelsMutex_);
            changed=backdrops_!=rectangles;
            backdrops_=std::move(rectangles);
        }
        if(changed&&browser_) browser_->GetHost()->Invalidate(PET_VIEW);
        return true;
    }
    void OnPaint(CefRefPtr<CefBrowser>,PaintElementType type,const RectList&,const void* buffer,int width,int height) override {
        if(type!=PET_VIEW || width<=0 || height<=0) return;
        std::lock_guard lock(pixelsMutex_);
        const auto size=static_cast<std::size_t>(width)*height*4;
        pixels_.resize(size); std::memcpy(pixels_.data(),buffer,size);
        paintWidth_=width;paintHeight_=height;paintedBackdrops_=backdrops_;dirty_=true;
    }
    void resize(int width,int height) {
        width_=width;height_=height;
        if(browser_) browser_->GetHost()->WasResized();
    }
    void installExtension(const std::string& id,const std::string& source) {
        extensions_[id]=source;
        if(ready_&&browser_) browser_->GetMainFrame()->ExecuteJavaScript(source,"xing:extension:"+id,1);
    }
    void setFrameRate(int fps) { if(browser_) browser_->GetHost()->SetWindowlessFrameRate(fps); }
    void submitFrame(const std::string& json) {
        {
            std::lock_guard lock(frameMutex_);
            latestFrame_ = json;
            if (frameQueued_) return;
            frameQueued_ = true;
        }
        CefRefPtr<SurfaceClient> current = this;
        postUi([current] {
            std::string latest;
            {
                std::lock_guard lock(current->frameMutex_);
                latest.swap(current->latestFrame_);
                current->frameQueued_ = false;
            }
            current->publish(latest);
        });
    }
    void publish(const std::string& json) {
        if (!ready_ || !browser_ || closing_) return;
        auto value = CefParseJSON(json, JSON_PARSER_RFC);
        if (!value) return;
        browser_->GetMainFrame()->ExecuteJavaScript(
            "window.xing?.receive(" + CefWriteJSON(value, JSON_WRITER_DEFAULT).ToString() + ");", "xing:bridge", 1);
        browser_->GetHost()->SendExternalBeginFrame();
    }
    void focusEditor(bool focused) {
        editorInput_ = focused;
        { std::lock_guard lock(commandMutex_); commands_.clear(); }
        if (browser_) browser_->GetHost()->SetFocus(focused);
    }
    void pointer(int kind, int x, int y, int button, int modifiers, int wheel) {
        if (!editorInput_ || !browser_) return;
        CefMouseEvent event;
        event.x = x; event.y = y; event.modifiers = modifiers;
        auto host = browser_->GetHost();
        if (kind == 0) host->SendMouseMoveEvent(event, false);
        else if (kind == 3) host->SendMouseWheelEvent(event, 0, wheel);
        else {
            const auto type = button == 1 ? MBT_RIGHT : button == 2 ? MBT_MIDDLE : MBT_LEFT;
            host->SendMouseClickEvent(event, type, kind == 2, 1);
        }
    }
    void key(int kind, int code, int modifiers) {
        if (!editorInput_ || !browser_) return;
        CefKeyEvent event;
        event.type = kind == 2 ? KEYEVENT_CHAR : kind == 1 ? KEYEVENT_KEYUP : KEYEVENT_RAWKEYDOWN;
        event.windows_key_code = code;
        event.character = kind == 2 ? static_cast<char16_t>(code) : 0;
        event.unmodified_character = event.character;
        event.modifiers = modifiers;
        browser_->GetHost()->SendKeyEvent(event);
    }
    std::vector<std::string> drainCommands() {
        std::lock_guard lock(commandMutex_);
        std::vector<std::string> result(commands_.begin(), commands_.end());
        commands_.clear();
        return result;
    }
    void reload() { ready_=false; if(browser_) browser_->ReloadIgnoreCache(); }
    void close() { closing_=true; if(browser_) browser_->GetHost()->CloseBrowser(true); }
    bool isClosed() { std::lock_guard lock(closeMutex_);return closed_; }
    bool waitClosed() { std::unique_lock lock(closeMutex_); return closeCondition_.wait_for(lock,std::chrono::seconds(5),[this]{return closed_;}); }
    int copy(void* destination,std::size_t capacity,int* dimensions,float* backdropOutput) {
        std::lock_guard lock(pixelsMutex_);
        if(!error_.empty()) throw std::runtime_error("HTML load failed: "+error_);
        dimensions[0]=paintWidth_;dimensions[1]=paintHeight_;dimensions[2]=static_cast<int>(paintedBackdrops_.size());
        for(std::size_t i=0;i<paintedBackdrops_.size();i++) std::copy(paintedBackdrops_[i].begin(),paintedBackdrops_[i].end(),backdropOutput+i*5);
        if(!dirty_) return 0;
        if(capacity<pixels_.size()) return -1;
        std::memcpy(destination,pixels_.data(),pixels_.size());dirty_=false;return 1;
    }
private:
    std::mutex frameMutex_;
    std::string latestFrame_;
    bool frameQueued_ = false;
    bool editorInput_ = false;
    std::mutex commandMutex_;
    std::deque<std::string> commands_;
    int width_,height_;
    CefRefPtr<CefBrowser> browser_;
    bool ready_=false,closing_=false;
    std::mutex pixelsMutex_,closeMutex_;
    std::condition_variable closeCondition_;
    bool closed_=false,dirty_=false;
    int paintWidth_=0,paintHeight_=0;
    std::vector<std::uint8_t> pixels_;
    std::vector<std::array<float,5>> backdrops_,paintedBackdrops_;
    std::string error_;
    std::map<std::string,std::string> extensions_;
    IMPLEMENT_REFCOUNTING(SurfaceClient);
};
CefRefPtr<SurfaceClient> client;
std::vector<CefRefPtr<SurfaceClient>> closingClients;
bool initialized=false;
DWORD ownerThread=0;
HMODULE runtimeLibrary=nullptr;

std::wstring string(JNIEnv* env,jstring value) {
    if(!value) throw std::invalid_argument("Missing web engine path");
    const jchar* chars=env->GetStringChars(value,nullptr);
    if(!chars) throw std::runtime_error("Cannot read web engine path");
    std::wstring result(reinterpret_cast<const wchar_t*>(chars),env->GetStringLength(value));
    env->ReleaseStringChars(value,chars);return result;
}
void fail(JNIEnv* env,const std::exception& error) {
    if(!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),error.what());
}
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_start(
        JNIEnv* env,jclass,jstring directory,jstring url,jstring cache,jint width,jint height,jint fps) {
    using namespace xing::web;
    try {
        if(width<1||height<1||width>16384||height>16384||fps<1||fps>1000) throw std::invalid_argument("Invalid browser dimensions or frame rate");
        if(client) throw std::runtime_error("Web engine already running");
        std::erase_if(closingClients,[](const auto& previous){return previous->isClosed();});
        const auto runtime=std::filesystem::path(string(env,directory));
        if(!initialized) {
            runtimeLibrary=LoadLibraryExW((runtime/L"libcef.dll").c_str(),nullptr,LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR|LOAD_LIBRARY_SEARCH_DEFAULT_DIRS);
            if(!runtimeLibrary) throw std::runtime_error("Cannot load the CEF runtime");
            CefMainArgs arguments(GetModuleHandleW(nullptr));
            CefSettings settings;
            settings.no_sandbox=true;settings.multi_threaded_message_loop=true;settings.windowless_rendering_enabled=true;
            CefString(&settings.browser_subprocess_path)=(runtime/L"xing-web-process.exe").wstring();
            CefString(&settings.resources_dir_path)=runtime.wstring();
            CefString(&settings.locales_dir_path)=(runtime/L"locales").wstring();
            CefString(&settings.root_cache_path)=string(env,cache);
            CefString(&settings.log_file)=(std::filesystem::path(string(env,cache))/L"cef.log").wstring();
            settings.log_severity=LOGSEVERITY_WARNING;
            if(!CefInitialize(arguments,settings,nullptr,nullptr)) throw std::runtime_error("CEF initialization failed; see cef.log");
            initialized=true;ownerThread=GetCurrentThreadId();
        }
        client=new SurfaceClient(width,height);
        CefWindowInfo window;window.SetAsWindowless(nullptr);window.external_begin_frame_enabled=true;
        CefBrowserSettings settings;settings.windowless_frame_rate=fps;settings.background_color=0;
        if(!CefBrowserHost::CreateBrowser(window,client,string(env,url),settings,nullptr,nullptr)) {
            client=nullptr;throw std::runtime_error("CEF browser creation failed");
        }
    } catch(const std::exception& error) { fail(env,error); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_resize(JNIEnv*,jclass,jint width,jint height) {
    using namespace xing::web;
    if(client&&width>0&&height>0&&width<=16384&&height<=16384) {
        auto current=client;postUi([current,width,height]{current->resize(width,height);});
    }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_setFrameRate(JNIEnv*,jclass,jint fps) {
    using namespace xing::web;
    if(client&&fps>=1&&fps<=1000) {auto current=client;postUi([current,fps]{current->setFrameRate(fps);});}
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_publish(JNIEnv* env,jclass,jstring data) {
    using namespace xing::web;
    try {
        if(!client) return;
        const auto json=CefString(string(env,data)).ToString();
        client->submitFrame(json);
    } catch(const std::exception& error) { fail(env,error); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_reload(JNIEnv*,jclass) {
    using namespace xing::web;
    if(client) { auto current=client;postUi([current]{current->reload();}); }
}
extern "C" JNIEXPORT jint JNICALL Java_dev_xingclient_render_web_NativeWebEngine_copyPixels(JNIEnv* env,jclass,jobject buffer,jintArray dimensions,jobject backdropBuffer) {
    using namespace xing::web;
    try {
        if(!client) return 0;
        auto* destination=env->GetDirectBufferAddress(buffer);
        const auto capacity=env->GetDirectBufferCapacity(buffer);
        auto* backdropOutput=static_cast<float*>(env->GetDirectBufferAddress(backdropBuffer));
        if(!destination||capacity<0||!dimensions||env->GetArrayLength(dimensions)!=3||!backdropOutput||env->GetDirectBufferCapacity(backdropBuffer)<256*5) throw std::invalid_argument("Invalid browser paint buffer");
        int size[3]={};const auto result=client->copy(destination,static_cast<std::size_t>(capacity),size,backdropOutput);
        env->SetIntArrayRegion(dimensions,0,3,size);return result;
    } catch(const std::exception& error) { fail(env,error);return 0; }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_stop(JNIEnv*,jclass) {
    using namespace xing::web;
    if(client) { auto current=client;closingClients.push_back(current);client=nullptr;postUi([current]{current->close();}); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_shutdown(JNIEnv* env,jclass) {
    using namespace xing::web;
    if(!initialized) return;
    if(GetCurrentThreadId()!=ownerThread) { fail(env,std::runtime_error("CEF shutdown must run on its initialization thread"));return; }
    if(client) { auto current=client;closingClients.push_back(current);client=nullptr;postUi([current]{current->close();}); }
    for(auto& current:closingClients) {
        if(!current->waitClosed()) { fail(env,std::runtime_error("CEF browser close timed out"));return; }
    }
    closingClients.clear();
    CefShutdown();initialized=false;
}

extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_installExtension(
        JNIEnv* env,jclass,jstring id,jstring source) {
    using namespace xing::web;
    try {
        if(!client) return;
        const auto key=CefString(string(env,id)).ToString();
        const auto script=CefString(string(env,source)).ToString();
        auto current=client;postUi([current,key,script]{current->installExtension(key,script);});
    } catch(const std::exception& error) {fail(env,error);}
}
extern "C" JNIEXPORT jstring JNICALL Java_dev_xingclient_render_web_NativeWebEngine_surfaceShader(JNIEnv* env,jclass,jint stage) {
    return env->NewStringUTF(stage==0?xing::web::surfaceVertex():xing::web::surfaceFragment());
}

extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_editorFocus(JNIEnv*, jclass, jboolean focused) {
    using namespace xing::web;
    if (client) { auto current = client; postUi([current, focused] { current->focusEditor(focused); }); }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_pointer(
        JNIEnv*, jclass, jint kind, jint x, jint y, jint button, jint modifiers, jint wheel) {
    using namespace xing::web;
    if (client && kind >= 0 && kind <= 3 && button >= 0 && button <= 2) {
        auto current = client;
        postUi([current, kind, x, y, button, modifiers, wheel] { current->pointer(kind, x, y, button, modifiers, wheel); });
    }
}
extern "C" JNIEXPORT void JNICALL Java_dev_xingclient_render_web_NativeWebEngine_key(
        JNIEnv*, jclass, jint kind, jint code, jint modifiers) {
    using namespace xing::web;
    if (client && kind >= 0 && kind <= 2 && code >= 0 && code <= 65535) {
        auto current = client; postUi([current, kind, code, modifiers] { current->key(kind, code, modifiers); });
    }
}
extern "C" JNIEXPORT jobjectArray JNICALL Java_dev_xingclient_render_web_NativeWebEngine_drainCommands(JNIEnv* env, jclass) {
    using namespace xing::web;
    const auto commands = client ? client->drainCommands() : std::vector<std::string>{};
    auto result = env->NewObjectArray(static_cast<jsize>(commands.size()), env->FindClass("java/lang/String"), nullptr);
    if (!result) return nullptr;
    for (std::size_t i = 0; i < commands.size(); ++i) {
        auto text = env->NewStringUTF(commands[i].c_str());
        if (!text) return result;
        env->SetObjectArrayElement(result, static_cast<jsize>(i), text);
        env->DeleteLocalRef(text);
    }
    return result;
}

#include <windows.h>
#include "include/cef_app.h"
#include "include/cef_v8.h"
#include "include/cef_process_message.h"

class BrowserReporter final : public CefV8Handler {
public:
    BrowserReporter(CefRefPtr<CefFrame> frame, const char* channel) : frame_(frame), channel_(channel) {}
    bool Execute(const CefString&,CefRefPtr<CefV8Value>,const CefV8ValueList& arguments,
                 CefRefPtr<CefV8Value>& result,CefString& exception) override {
        if(arguments.size()!=1 || !arguments[0]->IsString()) {exception="Expected JSON string";return true;}
        auto message=CefProcessMessage::Create(channel_);
        message->GetArgumentList()->SetString(0,arguments[0]->GetStringValue());
        frame_->SendProcessMessage(PID_BROWSER,message);
        result=CefV8Value::CreateUndefined();return true;
    }
private:
    CefRefPtr<CefFrame> frame_;
    const char* channel_;
    IMPLEMENT_REFCOUNTING(BrowserReporter);
};
class WebProcess final : public CefApp,public CefRenderProcessHandler {
public:
    CefRefPtr<CefRenderProcessHandler> GetRenderProcessHandler() override {return this;}
    void OnContextCreated(CefRefPtr<CefBrowser>,CefRefPtr<CefFrame> frame,CefRefPtr<CefV8Context> context) override {
        if (!frame->IsMain()) return;
        context->GetGlobal()->SetValue("__xingReportBackdrops",
            CefV8Value::CreateFunction("__xingReportBackdrops", new BrowserReporter(frame, "xing-backdrops")), V8_PROPERTY_ATTRIBUTE_READONLY);
        context->GetGlobal()->SetValue("__xingEditorCommand",
            CefV8Value::CreateFunction("__xingEditorCommand", new BrowserReporter(frame, "xing-editor")), V8_PROPERTY_ATTRIBUTE_READONLY);
    }
private:
    IMPLEMENT_REFCOUNTING(WebProcess);
};
int APIENTRY wWinMain(HINSTANCE instance,HINSTANCE,wchar_t*,int) {
    CefMainArgs arguments(instance);
    return CefExecuteProcess(arguments,new WebProcess(),nullptr);
}

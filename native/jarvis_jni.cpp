#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include <sys/stat.h>
#include "llama.h"

static std::mutex inference_lock;
static llama_model* cached_model = nullptr;
static std::string cached_path;
static long long cached_length = -1;
static long long cached_modified = -1;

static jstring message(JNIEnv* env, const char* text) {
    return env->NewStringUTF(text);
}

extern "C" JNIEXPORT jstring JNICALL
Java_hu_jarvis_local_OfflineBrain_generate(JNIEnv* env, jclass,
    jstring modelPath, jstring promptText, jint maxTokens) {
    if (!modelPath || !promptText) return message(env, "Hiányzó modell vagy kérdés.");
    std::lock_guard<std::mutex> lock(inference_lock);
    const char* model_chars=env->GetStringUTFChars(modelPath,nullptr);
    if (!model_chars) return message(env,"Nem olvasható a modell útvonala.");
    std::string path(model_chars);
    env->ReleaseStringUTFChars(modelPath,model_chars);
    const char* prompt_chars=env->GetStringUTFChars(promptText,nullptr);
    if (!prompt_chars) return message(env,"Nem olvasható a kérdés.");
    std::string prompt(prompt_chars);
    env->ReleaseStringUTFChars(promptText,prompt_chars);

    struct stat info;
    if (stat(path.c_str(),&info)!=0) return message(env,"A GGUF modellfájl nem található.");
    static std::once_flag initialized;
    std::call_once(initialized, [](){llama_backend_init();});
    if (!cached_model || cached_path!=path || cached_length!=(long long)info.st_size
        || cached_modified!=(long long)info.st_mtime) {
        if (cached_model) {llama_model_free(cached_model);cached_model=nullptr;}
        auto params=llama_model_default_params();
        params.n_gpu_layers=0;
        cached_model=llama_model_load_from_file(path.c_str(),params);
        if (!cached_model) return message(env,"A GGUF modell nem tölthető be vagy kevés a memória.");
        cached_path=path;
        cached_length=(long long)info.st_size;
        cached_modified=(long long)info.st_mtime;
    }

    auto context_params=llama_context_default_params();
    context_params.n_ctx=2048;
    context_params.n_batch=2048;
    context_params.n_ubatch=512;
    context_params.n_threads=4;
    context_params.n_threads_batch=4;
    llama_context* context=llama_init_from_model(cached_model,context_params);
    if (!context) return message(env,"Nincs elég RAM a helyi beszélgetéshez.");
    const llama_vocab* vocab=llama_model_get_vocab(cached_model);
    std::string input="Te JARVIS vagy, magyarul válaszoló helyi asszisztens. Válaszolj röviden és pontosan.\nFelhasználó: "+prompt+"\nJARVIS:";
    int n=llama_tokenize(vocab,input.c_str(),(int)input.size(),nullptr,0,true,true);
    if (n>=0) {llama_free(context);return message(env,"Érvénytelen modell-tokenizálás.");}
    n=-n;
    if (n>1600 || n<=0) {llama_free(context);return message(env,"A beszélgetés túl hosszú.");}
    std::vector<llama_token> tokens(n);
    n=llama_tokenize(vocab,input.c_str(),(int)input.size(),tokens.data(),(int)tokens.size(),true,true);
    if (n<=0 || llama_decode(context,llama_batch_get_one(tokens.data(),n))!=0) {
        llama_free(context);
        return message(env,"Nem sikerült feldolgozni a kérdést.");
    }

    llama_sampler_chain_params chain_params=llama_sampler_chain_default_params();
    llama_sampler* sampler=llama_sampler_chain_init(chain_params);
    llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.65f));
    llama_sampler_chain_add(sampler,llama_sampler_init_dist(42));
    std::string output;
    int limit = maxTokens>0 && maxTokens<=256 ? maxTokens : 128;
    for(int i=0;i<limit && n+i<2047;i++){
        llama_token token=llama_sampler_sample(sampler,context,-1);
        if(llama_vocab_is_eog(vocab,token)) break;
        char piece[512];
        int len=llama_token_to_piece(vocab,token,piece,sizeof(piece),0,true);
        if(len>0) output.append(piece,(size_t)len);
        llama_sampler_accept(sampler,token);
        if(llama_decode(context,llama_batch_get_one(&token,1))!=0) break;
    }
    llama_sampler_free(sampler);
    llama_free(context);
    if(output.empty()) output="A modell nem adott választ.";
    return env->NewStringUTF(output.c_str());
}

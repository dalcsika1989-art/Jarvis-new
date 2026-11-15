#include <jni.h>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"
static std::mutex inference_lock;
extern "C" JNIEXPORT jstring JNICALL
Java_hu_jarvis_local_OfflineBrain_generate(JNIEnv* env,jclass,jstring modelPath,jstring promptText,jint maxTokens) {
 std::lock_guard<std::mutex> lock(inference_lock);
 const char* rawPath=env->GetStringUTFChars(modelPath,nullptr);
 const char* rawPrompt=env->GetStringUTFChars(promptText,nullptr);
 std::string path(rawPath),prompt(rawPrompt);
 env->ReleaseStringUTFChars(modelPath,rawPath);
 env->ReleaseStringUTFChars(promptText,rawPrompt);
 llama_backend_init();
 auto mp=llama_model_default_params();
 mp.n_gpu_layers=0;
 llama_model* model=llama_model_load_from_file(path.c_str(),mp);
 if(!model) return env->NewStringUTF("Nem sikerült betölteni a helyi GGUF modellt.");
 auto cp=llama_context_default_params();
 cp.n_ctx=2048;cp.n_batch=512;cp.n_threads=4;cp.n_threads_batch=4;
 llama_context* ctx=llama_init_from_model(model,cp);
 if(!ctx){llama_model_free(model);return env->NewStringUTF("Nincs elegendő memória a modellhez.");}
 const llama_vocab* vocab=llama_model_get_vocab(model);
 std::string input="Te JARVIS vagy, magyarul válaszoló helyi asszisztens. Válaszolj röviden és pontosan.\nFelhasználó: "+prompt+"\nJARVIS:";
 int n=llama_tokenize(vocab,input.c_str(),(int)input.size(),nullptr,0,true,true);
 std::vector<llama_token> tokens(n>0?n:1);
 if(n<0)n=llama_tokenize(vocab,input.c_str(),(int)input.size(),tokens.data(),(int)tokens.size(),true,true);
 if(n<=0||n>1700){llama_free(ctx);llama_model_free(model);return env->NewStringUTF("A kérés túl hosszú.");}
 llama_batch batch=llama_batch_get_one(tokens.data(),n);
 if(llama_decode(ctx,batch)!=0){llama_free(ctx);llama_model_free(model);return env->NewStringUTF("A modell feldolgozása sikertelen.");}
 llama_sampler_chain_params sp=llama_sampler_chain_default_params();
 llama_sampler* sampler=llama_sampler_chain_init(sp);
 llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
 llama_sampler_chain_add(sampler,llama_sampler_init_dist(42));
 std::string result;
 int limit=maxTokens>0&&maxTokens<512?maxTokens:128;
 for(int i=0;i<limit;i++){
  llama_token token=llama_sampler_sample(sampler,ctx,-1);
  if(llama_vocab_is_eog(vocab,token))break;
  char piece[256];
  int len=llama_token_to_piece(vocab,token,piece,sizeof(piece),0,true);
  if(len>0)result.append(piece,len);
  llama_sampler_accept(sampler,token);
  llama_batch next=llama_batch_get_one(&token,1);
  if(llama_decode(ctx,next)!=0)break;
 }
 llama_sampler_free(sampler);
 llama_free(ctx);llama_model_free(model);
 if(result.empty())result="A modell nem adott választ.";
 return env->NewStringUTF(result.c_str());
}

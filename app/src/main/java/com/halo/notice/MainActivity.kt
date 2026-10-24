@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.halo.notice

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.view.WindowCompat

class MainActivity:ComponentActivity() {
    private val vm:NoticeViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent{
            SideEffect {
                WindowCompat.getInsetsController(window,window.decorView).apply {
                    isAppearanceLightStatusBars=!vm.dark
                    isAppearanceLightNavigationBars=!vm.dark
                }
            }
            NoticeTheme(vm.dark){NoticeScreen(vm)}
        }
    }
    override fun onResume(){super.onResume();vm.bridge.connect(false)}
}
private val Light=lightColorScheme(primary=Color(0xFF315F47),onPrimary=Color.White,primaryContainer=Color(0xFFD4EDBC),onPrimaryContainer=Color(0xFF153824),
    secondary=Color(0xFF526B60),secondaryContainer=Color(0xFFE0EBDF),tertiary=Color(0xFF765B88),tertiaryContainer=Color(0xFFEEDCF8),
    background=Color(0xFFF7F8F2),surface=Color(0xFFF7F8F2),surfaceContainer=Color(0xFFECEFE5),surfaceContainerLow=Color(0xFFF0F2EA),surfaceContainerHigh=Color(0xFFE5EADF))
@Composable private fun NoticeTheme(dark:Boolean,content:@Composable () -> Unit) {
    MaterialExpressiveTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFFB2D59B),primaryContainer=Color(0xFF315F47),tertiaryContainer=Color(0xFF503B60)) else Light,content=content)
}
private enum class Page(val title:String,val icon:ImageVector) {
    HOME("概览",Icons.Rounded.SpaceDashboard),CATEGORIES("类别",Icons.Rounded.Category),APPS("应用",Icons.Rounded.Apps),AI("云雀",Icons.Rounded.AutoAwesome),HISTORY("记录",Icons.Rounded.History)
}
@Composable private fun NoticeScreen(vm:NoticeViewModel) {
    var page by remember{mutableStateOf(Page.HOME)}
    var settings by remember{mutableStateOf(false)}
    val snack=remember{SnackbarHostState()}
    LaunchedEffect(vm.message){if(vm.message.isNotBlank()){snack.showSnackbar(vm.message);vm.message=""}}
    Scaffold(
        snackbarHost={SnackbarHost(snack)},
        topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=24.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(Icons.Rounded.NotificationsNone,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp))
            Text("知序",Modifier.padding(start=9.dp),fontSize=23.sp,fontWeight=FontWeight.Black)
            Spacer(Modifier.weight(1f))
            if(vm.busy||vm.scanning||vm.recommendations.running)LoadingIndicator(Modifier.size(32.dp))
            IconButton(onClick={vm.scan()},enabled=vm.connected&&!vm.busy&&!vm.scanning&&!vm.recommendations.running){Icon(Icons.Rounded.Refresh,"重新扫描")}
            IconButton(onClick={settings=true}){Icon(Icons.Rounded.Tune,"设置")}
        }},
        bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface){Page.entries.forEach{p->
            NavigationBarItem(selected=page==p,onClick={page=p},icon={Icon(p.icon,p.title)},label={Text(p.title)})
        }}}
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when(page) {
                Page.HOME->Home(vm,{page=it},{settings=true})
                Page.CATEGORIES->Categories(vm)
                Page.APPS->Applications(vm)
                Page.AI->Assistant(vm)
                Page.HISTORY->History(vm)
            }
        }
    }
    if(settings)SettingsSheet(vm){settings=false}
    vm.proposal?.let{p->ProposalSheet(vm,p){vm.proposal=null}}
}
@Composable private fun PageTitle(title:String,subtitle:String){Column(Modifier.padding(bottom=12.dp)){
    Text(title,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
    Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
}}
@Composable private fun Home(vm:NoticeViewModel,navigate:(Page)->Unit,settings:()->Unit) {
    val cs=vm.channels
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item{PageTitle("让通知，\n各归其位。","少一点打扰，留住真正重要的消息。")}
        item{PresetCarousel(vm)}
        item{YunqueRecommendations(vm,settings)}
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Metric("${cs.map{it.pkg}.distinct().size}","有通道的应用",Modifier.weight(1f)){navigate(Page.APPS)}
            Metric("${cs.size}","已读取通道",Modifier.weight(1f)){navigate(Page.CATEGORIES)}
            Metric("${cs.count{!it.enabled}}","已关闭通道",Modifier.weight(1f)){navigate(Page.CATEGORIES)}
        }}
        item{ConnectionCard(vm)}
        item{Card(onClick={navigate(Page.AI)},shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.tertiaryContainer)){
            Row(Modifier.padding(22.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(32.dp));Column(Modifier.weight(1f).padding(horizontal=14.dp)){
                Text("和云雀聊聊通知偏好",fontWeight=FontWeight.Bold,fontSize=18.sp)
                Text("“减少购物推送，保留快递和订单”",modifier=Modifier.padding(top=7.dp),style=MaterialTheme.typography.bodyMedium)
            };Icon(Icons.AutoMirrored.Rounded.ArrowForward,null)}
        }}
        item{Text("常见类别",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)}
        if(cs.isEmpty())item{Empty("还没有扫描数据","启动并授权 Shizuku，即可读取真实通道。",Icons.Rounded.Search)}
        items(cs.groupBy{it.name}.entries.sortedByDescending{it.value.size}.take(5),key={it.key}){(name,list)->
            CategoryRow(name,list){vm.prepare("关闭「$name」",list,ChangeMode.CLOSE)}
        }
        item{Text("只读取应用与通道设置；不读取通知正文。AI 对话时，查询到的通道元数据会发送至 DeepSeek。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=8.dp))}
    }
}
@Composable private fun Metric(value:String,label:String,modifier:Modifier,onClick:()->Unit){Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer)){
    Column(Modifier.padding(vertical=20.dp,horizontal=12.dp)){Text(value,fontSize=28.sp,fontWeight=FontWeight.Bold);Text(label,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}}
@Composable private fun ConnectionCard(vm:NoticeViewModel){Card(shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(vm.connected)Icons.Rounded.VerifiedUser else Icons.Rounded.Link,null,tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal=12.dp)){
            Text(if(vm.scanning)"正在扫描 ${vm.scanProgress}" else vm.bridgeStatus,fontWeight=FontWeight.SemiBold)
            Text(if(vm.lastScan>0)"上次扫描 ${date(vm.lastScan)}" else "首次扫描请允许读取已安装应用",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(!vm.connected)TextButton(onClick={vm.bridge.connect()}){Text("连接")}
    }
}}
@Composable private fun Search(value:String,onChange:(String)->Unit,hint:String){OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(28.dp),
    placeholder={Text(hint)},leadingIcon={Icon(Icons.Rounded.Search,null)},trailingIcon={if(value.isNotBlank())IconButton(onClick={onChange("")}){Icon(Icons.Rounded.Close,"清空搜索")}})}
@Composable private fun CategoryRow(name:String,list:List<Channel>,click:()->Unit){Card(onClick=click,shape=RoundedCornerShape(23.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
    Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
        Icon(if(list.all{!it.enabled})Icons.Rounded.NotificationsOff else Icons.Rounded.Label,null,tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(name.ifBlank{"未命名通道"},fontWeight=FontWeight.SemiBold);Text("${list.size} 个通道 · ${list.count{it.enabled}} 个开启",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null)
    }
}}
@Composable private fun Categories(vm:NoticeViewModel) {
    var query by remember{mutableStateOf("")};var chosen by remember{mutableStateOf<String?>(null)}
    val list=vm.visible.filter{Planning.matches(it,query)}
    val groups=list.groupBy{it.name}.entries.sortedWith(compareByDescending<Map.Entry<String,List<Channel>>>{it.value.size}.thenBy{it.key})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("同类通知，一起管理","名称相同的通道归为一组；近义类别交给助手判断。")}
        item{Search(query,{query=it},"搜索类别、描述或应用")}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("${groups.size} 个类别",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Text(if(vm.includeSystem)"包含系统应用" else "已隐藏系统应用",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        items(groups,key={it.key}){(name,channels)->CategoryRow(name,channels){chosen=name}}
        if(groups.isEmpty())item{Empty("没有匹配的类别","试试其他关键词，或重新扫描。",Icons.Rounded.Category)}
    }
    chosen?.let{name->ChannelSheet(vm,name,vm.visible.filter{it.name==name}){chosen=null}}
}
@Composable private fun Applications(vm:NoticeViewModel) {
    var query by remember{mutableStateOf("")};var chosen by remember{mutableStateOf<String?>(null)}
    val groups=vm.visible.groupBy{it.pkg}.values.filter{it.any{c->c.app.contains(query,true)||c.pkg.contains(query,true)}}.sortedBy{it.first().app}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{PageTitle("每个应用，都有分寸","查看通道开关、静默级别，以及系统设置。")}
        item{Search(query,{query=it},"搜索应用名称或包名")}
        items(groups,key={it.first().pkg}){cs->val c=cs.first();Card(onClick={chosen=c.pkg},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
            Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.secondaryContainer){Text(c.app.take(1),Modifier.padding(14.dp),fontWeight=FontWeight.Bold)}
                Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(c.app,fontWeight=FontWeight.Bold);Text("${cs.size} 个通道 · ${cs.count{it.enabled}} 个开启",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(!c.appEnabled)Text("应用通知总开关已关闭",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)}
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight,null)
            }
        }}
        if(groups.isEmpty())item{Empty("还没有匹配的应用","连接 Shizuku 并扫描后，这里会显示真实数据。",Icons.Rounded.Apps)}
    }
    chosen?.let{pkg->ChannelSheet(vm,vm.visible.firstOrNull{it.pkg==pkg}?.app?:pkg,vm.visible.filter{it.pkg==pkg}){chosen=null}}
}
@Composable private fun ChannelSheet(vm:NoticeViewModel,title:String,channels:List<Channel>,dismiss:()->Unit){ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
    LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.86f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilledTonalButton(onClick={dismiss();vm.prepare("关闭「$title」",channels,ChangeMode.CLOSE)},enabled=vm.connected&&!vm.scanning&&!vm.busy){Text("全部关闭")}
            OutlinedButton(onClick={dismiss();vm.prepare("开启「$title」",channels,ChangeMode.OPEN)},enabled=vm.connected&&!vm.scanning&&!vm.busy){Text("全部开启")}
        }}
        items(channels,key={it.key}){c->Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
            Column(Modifier.padding(16.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){
                    Text(c.name,fontWeight=FontWeight.Bold);Text(c.app,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                };Switch(checked=c.enabled,onCheckedChange={on->dismiss();vm.prepare("${if(on)"开启" else "关闭"}「${c.name}」",listOf(c),if(on)ChangeMode.OPEN else ChangeMode.CLOSE)},enabled=vm.connected&&!vm.busy&&!vm.scanning&&!vm.recommendations.running&&!c.critical)}
                if(c.description.isNotBlank())Text(c.description,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=6.dp))
                Text(importanceText(c.importance)+if(!c.appEnabled)" · 应用总开关关闭" else if(c.groupBlocked)" · 通道组关闭" else "",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(c.critical)Text("系统保护通道",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    TextButton(onClick={dismiss();vm.prepare("静默「${c.name}」",listOf(c),ChangeMode.QUIET)},enabled=vm.connected&&!vm.busy&&!vm.scanning&&!vm.recommendations.running&&!c.critical&&c.enabled){Text("设为静默")}
                    TextButton(onClick={vm.openSettings(c)}){Text("更多系统选项 ↗")}
                }
            }
        }}
    }
}}
@Composable private fun ProposalSheet(vm:NoticeViewModel,p:Proposal,dismiss:()->Unit) {
    var selected by remember(p){mutableStateOf(p.channels.filter{!it.critical&&vm.target(it,p.mode)!=it.importance}.map{it.key}.toSet())}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).padding(horizontal=20.dp)){
        Text("检查这次修改",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        Text(p.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp,bottom=8.dp))
        if(p.reason.isNotBlank())Text(p.reason,style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(bottom=10.dp))
        Text("只更改勾选项 · 保存原状态 · 支持撤销",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
        if(p.mode==ChangeMode.OPEN)Text("开启会恢复本地记录的级别；没有记录时使用默认级别。应用或通道组总开关仍需在系统设置开启。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
        LazyColumn(Modifier.weight(1f).padding(vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            items(p.channels,key={it.key}){c->val same=vm.target(c,p.mode)==c.importance
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow,RoundedCornerShape(20.dp)).padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=c.key in selected,onCheckedChange={check->selected=if(check)selected+c.key else selected-c.key},enabled=!c.critical&&!same)
                    Column(Modifier.weight(1f)){Text(c.name,fontWeight=FontWeight.SemiBold);Text(c.app,style=MaterialTheme.typography.bodySmall);Text(if(c.critical)"系统保护 · 不修改" else if(same)"已是目标状态 · 不修改" else "${importanceText(c.importance)} → ${importanceText(vm.target(c,p.mode))}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)}
                }
            }
        }
        Button(onClick={vm.applyProposal(selected)},enabled=selected.isNotEmpty()&&!vm.ai.running&&!vm.recommendations.running&&!vm.busy&&!vm.scanning&&vm.connected,modifier=Modifier.fillMaxWidth().height(56.dp)){
            Text(if(vm.ai.running||vm.recommendations.running)"等待云雀完成分析" else "应用 ${selected.size} 项修改",fontSize=16.sp)
        }
        TextButton(onClick=dismiss,modifier=Modifier.align(Alignment.CenterHorizontally)){Text("暂不修改")}
        Spacer(Modifier.navigationBarsPadding())
    }}
}
@Composable private fun Assistant(vm:NoticeViewModel) {
    val ai=vm.ai;val scope=rememberCoroutineScope();var input by remember{mutableStateOf("")};val scroll=rememberLazyListState()
    LaunchedEffect(ai.lines.size,ai.partial){if(ai.lines.isNotEmpty())scroll.animateScrollToItem(ai.lines.size)}
    Column(Modifier.fillMaxSize().imePadding()){
        LazyColumn(Modifier.weight(1f),state=scroll,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
            item{PageTitle("和云雀聊聊","你的通知助手 · DeepSeek Flash")}
            if(ai.lines.isEmpty())item{
                Card(shape=RoundedCornerShape(30.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.tertiaryContainer)){
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                        Icon(Icons.Rounded.AutoAwesome,null,Modifier.size(36.dp));Text("把打扰交给我，\n把重要留给你。",fontSize=25.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold)
                        Text("我会查询真实通道，整理成可逐项检查的方案。你点击应用后才会更改设置。")
                    }
                }
                listOf("关闭所有应用的营销推广通知","减少购物促销，保留物流和订单","帮我看看哪些通知类别最值得整理").forEach{text->
                    OutlinedButton(onClick={ai.send(text,scope)},enabled=!ai.running&&!vm.recommendations.running&&!vm.busy&&!vm.scanning,modifier=Modifier.fillMaxWidth().padding(top=10.dp)){Text(text)}
                }
            }
            items(ai.lines){line->ChatBubble(line)}
            if(ai.partial.isNotBlank())item{ChatBubble(ChatLine("assistant",ai.partial))}
            if(ai.running)item{Row(verticalAlignment=Alignment.CenterVertically){LoadingIndicator(Modifier.size(30.dp));Text(ai.status,Modifier.padding(start=10.dp),style=MaterialTheme.typography.labelMedium)}}
        }
        if(ai.tokens>0)Text("输入 ${ai.tokens} tokens · 缓存命中 ${ai.cachedTokens} · 已压缩 ${ai.compactions} 次",Modifier.padding(horizontal=22.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!vm.hasKey)Text("请在右上角设置中配置 API Key",Modifier.padding(horizontal=22.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.error)
        Row(Modifier.padding(14.dp).fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(input,{input=it},Modifier.weight(1f),placeholder={Text("例如：关闭推广，保留快递")},shape=RoundedCornerShape(26.dp),maxLines=4,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Send),keyboardActions=KeyboardActions(onSend={if(!ai.running&&!vm.recommendations.running&&!vm.busy&&!vm.scanning){ai.send(input,scope);input=""}}))
            FilledIconButton(onClick={if(ai.running)ai.cancel() else{ai.send(input,scope);input=""}},enabled=ai.running||(input.isNotBlank()&&!vm.busy&&!vm.scanning&&!vm.recommendations.running),modifier=Modifier.size(56.dp)){
                Icon(if(ai.running)Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.Send,if(ai.running)"停止" else "发送")
            }
        }
    }
}
@Composable private fun ChatBubble(line:ChatLine){val user=line.role=="user";Column(Modifier.fillMaxWidth(),horizontalAlignment=if(user)Alignment.End else Alignment.Start){
    Text(when(line.role){"user"->"你";"assistant"->"云雀";else->"本机记录"},style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=5.dp,bottom=5.dp))
    Surface(shape=RoundedCornerShape(if(user)26.dp else 8.dp,26.dp,26.dp,26.dp),color=if(user)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow){
        Text(line.text,Modifier.padding(18.dp),style=MaterialTheme.typography.bodyLarge)
    }
}}
@Composable private fun History(vm:NoticeViewModel){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
    item{PageTitle("每次整理，都有来路","记录逐项结果，可恢复修改前的通知级别。")}
    if(vm.history.isEmpty())item{Empty("还没有修改记录","应用方案后，会在这里记录原状态与回读结果。",Icons.Rounded.History)}
    items(vm.history,key={it.id}){b->var expanded by remember{mutableStateOf(false)}
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)){
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text(b.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(date(b.time),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${b.edits.count{it.state=="done"}} 项修改生效 · ${b.edits.count{it.state=="undone"}} 项已撤销 · ${b.edits.count{it.state=="failed"}} 项失败",style=MaterialTheme.typography.bodyMedium)
                Row{TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起详情" else "逐项结果")};Spacer(Modifier.weight(1f))
                    FilledTonalButton(onClick={vm.undo(b)},enabled=b.edits.any{it.state=="done"}&&vm.connected&&!vm.busy&&!vm.scanning&&!vm.ai.running&&!vm.recommendations.running){Text("撤销修改")}}
                if(expanded)b.edits.forEach{e->Column(Modifier.padding(vertical=5.dp)){
                    Text("${e.channel.app} · ${e.channel.name}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)
                    Text("${importanceText(e.before)} → ${importanceText(e.after)} · "+when(e.state){"done"->"已回读";"undone"->"已恢复";"failed"->"失败";else->"待核对"},style=MaterialTheme.typography.bodySmall)
                    if(e.error.isNotBlank())Text(e.error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                }}
            }
        }
    }
}}
@Composable private fun SettingsSheet(vm:NoticeViewModel,dismiss:()->Unit){var key by remember{mutableStateOf("")};var pref by remember{mutableStateOf(vm.ai.preferences)};var clear by remember{mutableStateOf(false)}
    val context=androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){LazyColumn(Modifier.fillMaxHeight(.92f),contentPadding=PaddingValues(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
        item{Text("按你的习惯",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
        item{ConnectionCard(vm)}
        item{TextButton(onClick={runCatching{context.startActivity(context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?:Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/")))}}){Text("打开 Shizuku / 查看启动说明 ↗")}}
        item{SettingSwitch("显示系统应用","系统通知通常承担设备状态与安全提醒",vm.includeSystem){vm.includeSystem=it;vm.preferences()}}
        item{SettingSwitch("深色外观","柔和的深色表面",vm.dark){vm.dark=it;vm.preferences()}}
        item{HorizontalDivider()}
        item{Text("云雀 · AI 连接",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("deepseek-flash\nhttps://api.deepseek.com/responses",style=MaterialTheme.typography.bodyMedium)}
        item{Text(if(vm.hasKey)"已配置 · 使用 Android Keystore 加密存储" else "尚未配置 API Key",color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium)}
        item{OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("新的 DeepSeek API Key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),shape=RoundedCornerShape(20.dp))}
        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Button(onClick={vm.saveKey(key);key=""},enabled=key.isNotBlank()&&!vm.ai.running&&!vm.recommendations.running){Text("保存密钥")}
            TextButton(onClick={vm.saveKey("")},enabled=vm.hasKey&&!vm.ai.running&&!vm.recommendations.running){Text("移除密钥")}
        }}
        item{OutlinedTextField(pref,{pref=it},Modifier.fillMaxWidth(),label={Text("持续保留的通知偏好")},minLines=3,shape=RoundedCornerShape(20.dp))}
        item{FilledTonalButton(onClick={vm.ai.preferences=pref.take(4000);vm.ai.persist();vm.message="偏好已保存"},enabled=!vm.ai.running&&!vm.recommendations.running){Text("保存偏好")}}
        item{Text("上下文在本机持久保存；较早对话会压缩为摘要，近期对话和完整工具调用保留。AI 仅接收你发送的对话、偏好与查询到的通道元数据，不读取通知内容。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        item{TextButton(onClick={clear=true},enabled=!vm.ai.running){Text("清空 AI 对话与摘要")}}
        item{Text("知序 0.2.0\n通知开关、静默与批量恢复通过 Shizuku 系统服务实现。锁屏、悬浮、角标等厂商专用选项可进入“更多系统选项”设置。只管理当前用户已创建的通道，后续新增通道需要重新扫描。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }}
    if(clear)AlertDialog(onDismissRequest={clear=false},title={Text("清空对话？")},text={Text("删除本机 AI 对话及摘要，通知偏好与修改记录会保留。")},confirmButton={TextButton(onClick={vm.ai.clear();clear=false}){Text("清空")}},dismissButton={TextButton(onClick={clear=false}){Text("取消")}})
}
@Composable private fun SettingSwitch(title:String,subtitle:String,value:Boolean,set:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).padding(end=12.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(value,set)}}
@Composable private fun Empty(title:String,subtitle:String,icon:ImageVector){Column(Modifier.fillMaxWidth().padding(vertical=36.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
    Icon(icon,null,Modifier.size(44.dp),tint=MaterialTheme.colorScheme.primary);Text(title,fontWeight=FontWeight.Bold,fontSize=19.sp);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
}}
private fun date(time:Long)=SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date(time))

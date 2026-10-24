@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.halo.notice

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun PresetCarousel(vm:NoticeViewModel) {
    val pager=rememberPagerState(pageCount={HomePreset.entries.size})
    val scope=rememberCoroutineScope()
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        HorizontalPager(state=pager,pageSpacing=12.dp,modifier=Modifier.fillMaxWidth().semantics{contentDescription="通知预设轮播，第 ${pager.currentPage+1} / ${pager.pageCount} 张"}) { index ->
            val preset=HomePreset.entries[index]
            val channels=preset.matches(vm.channels)
            val actionable=channels.count{!it.critical&&vm.target(it,preset.mode)!=it.importance}
            val color=when(preset){HomePreset.MARKETING->MaterialTheme.colorScheme.primaryContainer;HomePreset.OFFERS->MaterialTheme.colorScheme.tertiaryContainer;HomePreset.SOCIAL->MaterialTheme.colorScheme.secondaryContainer;HomePreset.HOT->MaterialTheme.colorScheme.surfaceContainerHigh}
            val icon=when(preset){HomePreset.MARKETING->Icons.Rounded.NotificationsOff;HomePreset.OFFERS->Icons.Rounded.LocalOffer;HomePreset.SOCIAL->Icons.AutoMirrored.Rounded.Chat;HomePreset.HOT->Icons.AutoMirrored.Rounded.VolumeOff}
            Card(shape=RoundedCornerShape(36.dp),colors=CardDefaults.cardColors(containerColor=color),modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.onSurface.copy(alpha=.07f)){Icon(icon,null,Modifier.padding(13.dp).size(28.dp))}
                        Spacer(Modifier.weight(1f));Text("通知预设 · ${index+1} / ${pager.pageCount}",style=MaterialTheme.typography.labelLarge)
                    }
                    Text(preset.title,fontSize=29.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold)
                    Text(if(vm.lastScan==0L)"扫描后，按「${preset.category}」匹配真实通道。" else "找到 ${vm.channels.count{it.name==preset.category}} 个同名通道 · $actionable 个可调整",style=MaterialTheme.typography.bodyMedium,minLines=2)
                    Button(onClick={if(!vm.connected)vm.bridge.connect() else vm.prepare(preset.action,channels,preset.mode)},
                        enabled=!vm.busy&&!vm.scanning&&!vm.recommendations.running&&!vm.ai.running,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {
                        Icon(if(vm.connected)Icons.Rounded.DoneAll else Icons.Rounded.Link,null);Spacer(Modifier.width(9.dp))
                        Text(if(vm.connected)preset.action else "连接 Shizuku，开始整理",fontSize=16.sp)
                    }
                    Text(if(preset.mode==ChangeMode.QUIET)"已关闭或已静默的通道保持原状" else "精确匹配同名类别 · 应用前逐项检查",style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("左滑查看更多预设",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                repeat(pager.pageCount){i->
                    val width by animateDpAsState(if(i==pager.currentPage)24.dp else 7.dp,label="预设指示器")
                    Box(Modifier.sizeIn(minWidth=24.dp,minHeight=32.dp).semantics{contentDescription="切换到${HomePreset.entries[i].category}预设"}
                        .clickable{scope.launch{pager.animateScrollToPage(i)}},contentAlignment=Alignment.Center) {
                        Box(Modifier.size(width,7.dp).background(if(i==pager.currentPage)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(8.dp)))
                    }
                }
            }
        }
    }
}

@Composable internal fun YunqueRecommendations(vm:NoticeViewModel,settings:()->Unit) {
    val agent=vm.recommendations;val bundle=agent.bundle
    val changed=remember(vm.channels,vm.ai.preferences,bundle){bundle!=null&&RecommendationRules.fingerprint(vm.channels,vm.ai.preferences)!=bundle.fingerprint}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome,null,tint=MaterialTheme.colorScheme.tertiary,modifier=Modifier.size(26.dp))
            Text("云雀建议",Modifier.weight(1f).padding(start=9.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            if(bundle!=null&&!agent.running)TextButton(onClick={vm.analyzeRecommendations()},enabled=!vm.busy&&!vm.scanning&&!vm.ai.running){Text("重新分析")}
        }
        Card(shape=RoundedCornerShape(28.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(agent.running) {
                    Row(verticalAlignment=Alignment.CenterVertically){LoadingIndicator(Modifier.size(38.dp));Column(Modifier.padding(start=12.dp)){
                        Text("云雀正在通盘考虑",fontWeight=FontWeight.Bold,fontSize=18.sp)
                        Text(agent.stage,style=MaterialTheme.typography.bodySmall)
                    }}
                    LinearProgressIndicator(progress={if(agent.total>0)agent.analyzed.toFloat()/agent.total else 0f},modifier=Modifier.fillMaxWidth())
                    Text("已分析 ${agent.analyzed} / ${agent.total} 个通道，全部完成后展示建议。",style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={agent.cancel()}){Text("停止分析")}
                } else if(bundle==null) {
                    Text("先了解全部，再给你建议。",fontWeight=FontWeight.Bold,fontSize=21.sp)
                    Text("云雀会分析全部已扫描通道、长期偏好和近期操作，整理出可执行的通知优化建议。",style=MaterialTheme.typography.bodyMedium)
                    Button(onClick={if(!vm.hasKey)settings() else vm.analyzeRecommendations()},enabled=!vm.busy&&!vm.scanning&&!vm.ai.running,modifier=Modifier.fillMaxWidth()) {
                        Text(if(!vm.hasKey)"配置云雀，开始分析" else if(agent.resumable)"继续分析全部通知" else "让云雀分析全部通知")
                    }
                    if(agent.resumable)Text("已保存 ${agent.analyzed} / ${agent.total} 个通道的分析进度，信息未变时可继续。",style=MaterialTheme.typography.bodySmall)
                    Text("分析会将通道设置元数据发送至 DeepSeek；不读取通知正文。",style=MaterialTheme.typography.bodySmall)
                } else {
                    Text("已看过 ${bundle.appCount} 个应用、${bundle.channelCount} 个通道",fontWeight=FontWeight.Bold,fontSize=18.sp)
                    Text("${SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date(bundle.createdAt))} 完成 · ${bundle.advice.size} 条可执行建议",style=MaterialTheme.typography.bodyMedium)
                    Text(if(changed)"有设置或偏好更新：已达成的建议会标记完成，相关设置变化的建议需要重新分析。" else "结合你的偏好保留重要提醒，每条建议都可以检查后交给云雀执行。",style=MaterialTheme.typography.bodySmall)
                    if(bundle.advice.isEmpty())Text("本次没有发现适合调整的通道，保持当前设置即可。",style=MaterialTheme.typography.bodyMedium)
                }
                if(agent.error.isNotBlank())Text(agent.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(!agent.running)bundle?.advice?.forEach{advice->
            val remaining=vm.adviceRemaining(advice)
            var expanded by remember(advice.id){mutableStateOf(false)}
            Card(shape=RoundedCornerShape(25.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(advice.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        SuggestionChip(onClick={expanded=!expanded},label={Text(if(remaining?.isEmpty()==true)"已达成" else "${advice.channels.size} 个通道")})
                    }
                    Text(advice.reason,style=MaterialTheme.typography.bodyMedium,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
                    Text(advice.channels.map{it.app}.distinct().let{apps->apps.take(4).joinToString("、")+if(apps.size>4)"等 ${apps.size} 个应用" else ""},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起原因" else "查看原因")}
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick={vm.reviewAdvice(advice)},enabled=remaining?.isNotEmpty()==true&&vm.connected&&!vm.busy&&!vm.scanning&&!vm.ai.running) {
                            Text(when{remaining==null->"需重新分析";remaining.isEmpty()->"已达成";else->"让云雀执行"})
                        }
                    }
                }
            }
        }
    }
}

import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import {HttpClientModule} from '@angular/common/http';
import { NgClass } from '@angular/common';
import { ModalModule } from 'ngx-bootstrap/modal';
import { FormsModule } from '@angular/forms';

// Components
import { OlMapComponent } from './openlayermap/olmap.component';
import { OlMapPreviewComponent } from './layerpanel/infopanel/openlayermappreview/olmap.preview.component';
import { LayerPanelComponent } from './layerpanel/layerpanel.component';
import { FilterPanelComponent } from './layerpanel/filterpanel/filterpanel.component';
import { DownloadPanelComponent } from './layerpanel/downloadpanel/downloadpanel.component';
import { InfoPanelComponent} from './layerpanel/infopanel/infopanel.component';
import { NgbdModalStatusReportComponent } from './toppanel/renderstatus/renderstatus.component';


import { PortalCoreModule } from './portal-core-ui/portal-core.module'

@NgModule({
  declarations: [
    OlMapComponent,
    OlMapPreviewComponent,
    LayerPanelComponent,
    FilterPanelComponent,
    DownloadPanelComponent,
    NgbdModalStatusReportComponent,
    InfoPanelComponent
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpClientModule,
    PortalCoreModule,
    ModalModule.forRoot()
  ],
  entryComponents: [NgbdModalStatusReportComponent],
  bootstrap: [OlMapComponent, LayerPanelComponent]
})
export class AppModule { }

package agroscience.fields.services;

import agroscience.fields.dao.models.FieldAndCurrentCrop;
import agroscience.fields.dao.models.FieldAndCurrentCropImpl;
import agroscience.fields.dao.entities.CropRotation;
import agroscience.fields.dao.entities.Field;
import agroscience.fields.dao.repositories.FieldRepository;
import agroscience.fields.dao.repositories.JBDCFieldDao;
import agroscience.fields.dto.ResponseMeteo;
import agroscience.fields.dto.field.CoordinatesWithFieldId;
import agroscience.fields.dto.field.RequestField;
import agroscience.fields.dto.field.ResponseFullField;
import agroscience.fields.exceptions.DuplicateException;
import agroscience.fields.mappers.FieldMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FieldService {
    private final FieldRepository fRepository;
    private final FieldMapper fMapper;
    private final JBDCFieldDao jbdcFieldDao;
    private final RestTemplate restTemplate;

    public FieldAndCurrentCrop createField(Field field){
        try {
            return new FieldAndCurrentCropImpl(fRepository.save(field), new CropRotation());
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("Поле с именем " + field.getName() + " уже существует", "name");
        }
    }

    public ResponseFullField getFullField(Long id){
        var FCRSC = fRepository.getFullField(id);

        List<ResponseMeteo> meteoList;
//        try {
            ResponseEntity<List<ResponseMeteo>> response = restTemplate.exchange(
                    "http://meteo-back:8003/api/v1/meteo/" + id,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ResponseMeteo>>() {}
            );

//            if (response.getStatusCode().is2xxSuccessful()) {
                meteoList = response.getBody();
//            } else {
//                throw new RuntimeException("From meteo " + response.getStatusCode());
//            }
//        } catch (Exception e) {
//            meteoList = null;
//        }

        if(FCRSC == null){
            throw new EntityNotFoundException("Не найдено поле с id: "+id);
        }else if (FCRSC.getField() == null){
            throw new EntityNotFoundException("Не найдено поле с id: "+id);
        }

        return fMapper.fieldToResponseFullField(FCRSC, meteoList);
    }

    public FieldAndCurrentCrop getFieldWithCurrentCrop(Long id){
        var fieldAndCropRotation = fRepository.fieldWithLatestCrop(id);
        if(fieldAndCropRotation.getField() == null){
            throw new EntityNotFoundException("Не найдено поле с id: "+id);
        }
        return fieldAndCropRotation;
    }

    public List<FieldAndCurrentCrop> getFields(Long orgid, Pageable page){
        return fRepository.fieldsWithLatestCrops(orgid,page).toList();
    }

    public FieldAndCurrentCrop updateField(Long id, RequestField request) {
        var fieldWithCrop =  fRepository.fieldWithLatestCrop(id);
        if(fieldWithCrop == null){
            throw new EntityNotFoundException("Не найдено поле с id: "+id);
        }
        var field = fieldWithCrop.getField();
        fMapper.requestFieldToField(field, request);
        try {
            fRepository.save(field);
        }catch (DataIntegrityViolationException ex) {
            throw new DuplicateException("Поле с именем " + field.getName() + " уже существует", "name");
        }
        return fieldWithCrop;
    }

    public void deleteField(Long id){
        if(!fRepository.existsById(id)){
            throw new EntityNotFoundException("Не найдено поле с id: " + id);
        }
        fRepository.deleteById(id);
    }

    public List<FieldAndCurrentCrop> getFieldsForPreview(Long orgId, Pageable pageable){
        return fRepository.fieldsWithLatestCrops(orgId, pageable).toList();
    }

    public List<CoordinatesWithFieldId> getAllCoordinates(){
        return jbdcFieldDao.getAllCoordinates();
    }
}
